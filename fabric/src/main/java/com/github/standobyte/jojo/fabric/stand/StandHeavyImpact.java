package com.github.standobyte.jojo.fabric.stand;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.core.StandStatFormulas;
import com.github.standobyte.jojo.fabric.stand.restoration.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Shared active HeavyPunchBlockInstance and KnockbackCollisionImpact, including source chunk history. */
public final class StandHeavyImpact {
    public static final EntityType<StandBlockShardEntity> SHARD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            RipplesFabric.id("block_shard"), EntityType.Builder.<StandBlockShardEntity>of(StandBlockShardEntity::new, MobCategory.MISC)
                    .sized(.5F, .5F).clientTrackingRange(4).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("block_shard"))));
    private static final AttachmentType<CompoundTag> SAVED = AttachmentRegistry.<CompoundTag>builder().initializer(CompoundTag::new)
            .persistent(CompoundTag.CODEC).buildAndRegister(RipplesFabric.id("stand_knockback_impact"));
    private static final Map<Entity, Impact> IMPACTS = new IdentityHashMap<>();
    private static boolean initialized;
    private static final class Impact {
        LivingEntity attacker; Vec3 direction, previous; double power, minCos = 1; float radius; boolean hadBlock;
        List<BlockPos> destroyed;
        boolean active() { return direction != null; }
        double strength() { return power * minCos; }
        void reset() { direction = null; power = 0; radius = 0; }
        CompoundTag save() {
            var tag = new CompoundTag();
            if (active()) { tag.putDouble("X", direction.x); tag.putDouble("Y", direction.y); tag.putDouble("Z", direction.z);
                tag.putDouble("Power", power); tag.putDouble("MinCos", minCos); tag.putFloat("ExplosionRadius", radius); tag.putBoolean("HadBlockImpact", hadBlock); }
            return tag;
        }
        static Impact load(CompoundTag tag) {
            var result = new Impact();
            if (tag.contains("X")) {
                result.direction = new Vec3(tag.getDoubleOr("X", 0), tag.getDoubleOr("Y", 0), tag.getDoubleOr("Z", 0));
                result.power = tag.getDoubleOr("Power", 0); result.minCos = tag.getDoubleOr("MinCos", 0);
                result.radius = tag.getFloatOr("ExplosionRadius", 0); result.hadBlock = tag.getBooleanOr("HadBlockImpact", false);
            }
            return result;
        }
    }
    public record BlockVisual(BlockPos pos, int state) { }
    public record ShockwavePayload(List<BlockVisual> blocks) implements CustomPacketPayload {
        public static final Type<ShockwavePayload> TYPE = new Type<>(RipplesFabric.id("heavy_block_shockwave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShockwavePayload> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.blocks.size()); for (var block : p.blocks) { b.writeBlockPos(block.pos); b.writeVarInt(block.state); }
        }, b -> {
            int count = b.readVarInt(); if (count < 0 || count > 20000) throw new IllegalArgumentException("Invalid shockwave block count");
            var blocks = new ArrayList<BlockVisual>(count); for (int i = 0; i < count; i++) blocks.add(new BlockVisual(b.readBlockPos(), b.readVarInt()));
            return new ShockwavePayload(List.copyOf(blocks));
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void initialize() {
        if (initialized) return; initialized = true;
        PayloadTypeRegistry.clientboundPlay().register(ShockwavePayload.TYPE, ShockwavePayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(StandHeavyImpact::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearRuntime());
    }
    private static Impact get(Entity entity) {
        var impact = IMPACTS.get(entity);
        if (impact == null) { var saved = entity.getAttached(SAVED); if (saved != null && !saved.isEmpty()) { impact = Impact.load(saved); IMPACTS.put(entity, impact); } }
        return impact;
    }
    public static void start(StandManifestation stand, LivingEntity target, double power, double precision) {
        var velocity = target.getDeltaMovement(); double resistance = 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance <= 0 || velocity.lengthSqr() == 0) return;
        VampireCollisionImpact.clear(target);
        var impact = new Impact(); impact.attacker = stand.entity(); impact.direction = velocity.normalize(); impact.power = velocity.length() * resistance;
        impact.radius = Math.max(Math.min((float) power * .175F, 10) - .5F, 0); IMPACTS.put(target, impact); target.setAttached(SAVED, impact.save());
    }
    public static boolean active(Entity entity) { var impact = get(entity); return impact != null && impact.active(); }
    public static List<BlockPos> destroyedBlocks(Entity entity) { var impact = get(entity); return impact == null || impact.destroyed == null ? List.of() : List.copyOf(impact.destroyed); }
    public static void tick(ServerLevel level) {
        // Loading reconstructs only source-saved impact fields; attacker and last explosion remain transient.
        for (var entity : level.getAllEntities()) {
            var impact = get(entity); if (impact == null || !impact.active() || TimeStopService.isFrozen(entity)) continue;
            var movement = entity.getDeltaMovement();
            if (impact.power <= 0 || Math.abs(movement.x) < 1E-7 && Math.abs(movement.z) < 1E-7 || movement.normalize().dot(impact.direction) <= 0) impact.reset();
            else {
                impact.minCos = Math.min(impact.minCos, movement.normalize().dot(impact.direction)); impact.power = Math.min(impact.power, movement.length());
                if (impact.previous != null && Math.abs(impact.previous.x - entity.getX()) < 1E-7 && Math.abs(impact.previous.z - entity.getZ()) < 1E-7)
                    collide(entity, movement, movement);
                impact.previous = entity.position();
            }
            entity.setAttached(SAVED, impact.save());
        }
        IMPACTS.keySet().removeIf(entity -> entity.isRemoved());
    }
    public static void clear(Entity entity) { IMPACTS.remove(entity); entity.removeAttached(SAVED); }
    public static void clearRuntime() { IMPACTS.clear(); }
    private static LivingEntity user(LivingEntity attacker) { return attacker instanceof StandManifestation stand ? stand.owner() : attacker; }
    private static boolean canHarm(LivingEntity attacker, Entity target) {
        var user = user(attacker);
        return target != attacker && target != user && !(target instanceof LivingEntity living && attacker != null && !attacker.canAttack(living))
                && !(target instanceof Player player && user instanceof Player playerUser && !playerUser.canHarmPlayer(player));
    }
    private static boolean allowed(ServerLevel level, BlockPos pos, LivingEntity attacker) {
        var player = user(attacker); var state = level.getBlockState(pos);
        return !state.isAir() && !level.isOutsideBuildHeight(pos) && (!(player instanceof Player user)
                || user.mayBuild() && level.mayInteract(user, pos) && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, user, pos, state, level.getBlockEntity(pos)));
    }
    public static void punchBlock(StandManifestation stand, BlockHitResult hit, double power, double precision) {
        if (!(stand.entity().level() instanceof ServerLevel level)) return;
        var pos = hit.getBlockPos(); var state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos) * (state.requiresCorrectToolForDrops() ? 1 : .6F);
        int harvest = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2 : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
        if (level.getGameRules().get(JojoGameRules.BREAK_BLOCKS) && StandStatFormulas.isBlockBreakable(power, hardness, harvest) && allowed(level, pos, stand.entity()))
            level.destroyBlock(pos, stand.owner() == null || !stand.owner().isCreative(), stand.entity());
        var center = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(.6));
        explode(level, stand.entity(), center, stand.entity().getLookAngle(), Math.min((float) power * .175F, 10), (float) power * .4F,
                null, level.getGameRules().get(JojoGameRules.BREAK_BLOCKS), true, power, precision);
    }
    /** Called before the entity moves, at vanilla collision resolution. */
    public static void collide(Entity entity, Vec3 movement, Vec3 actual) {
        if (!(entity.level() instanceof ServerLevel level) || TimeStopService.isFrozen(entity) || movement.lengthSqr() < 1E-7) return;
        var impact = get(entity); if (impact == null || !impact.active()) return;
        var box = entity.getBoundingBox().inflate(.25); var swept = box.expandTowards(movement);
        var potential = level.getEntities(entity, swept.inflate(1E-7), target -> target.isPickable() && !target.isSpectator()
                && !(target instanceof Player p && p.isCreative()) && canHarm(impact.attacker, target)
                && (!(entity instanceof LivingEntity living) || canHarm(living, target)));
        var collided = new ArrayList<Entity>();
        double y = collideAxis(Direction.Axis.Y, box, movement.y, potential, collided);
        if (y != 0) box = box.move(0, y, 0);
        boolean zFirst = Math.abs(movement.x) < Math.abs(movement.z);
        if (zFirst) collideAxis(Direction.Axis.Z, box, movement.z, potential, collided);
        collideAxis(Direction.Axis.X, box, movement.x, potential, collided);
        if (!zFirst) collideAxis(Direction.Axis.Z, box, movement.z, potential, collided);
        for (var target : collided) {
            float damage = (float) impact.strength() * 5;
            var source = new DamageSource(level.registryAccess().getOrThrow(VampireCombatService.COLLISION_DAMAGE), entity);
            hurt(level, target, source, damage, impact.attacker);
            if (target instanceof LivingEntity living) { living.knockback(impact.strength(), -entity.getDeltaMovement().x, -entity.getDeltaMovement().z, source, damage); living.syncVelocity = true; }
        }
        if (movement.equals(actual)) return;
        var blocks = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.of(entity));
            if (!shape.isEmpty() && shape.bounds().move(pos).intersects(swept) && !shape.bounds().move(pos).intersects(entity.getBoundingBox().deflate(1E-7))) blocks.add(pos.immutable());
        }
        if (blocks.isEmpty()) return;
        blocks.sort(Comparator.comparingDouble(pos -> manhattan(level.getBlockState(pos).getCollisionShape(level, pos).bounds().move(pos), entity.getBoundingBox())));
        double remaining = impact.power; float wallDamage = 0; boolean glass = false;
        for (var pos : blocks) {
            var state = level.getBlockState(pos); float hardness = state.getDestroySpeed(level, pos); if (!state.requiresCorrectToolForDrops()) hardness *= .6F;
            float cost = hardness < 0 ? 1 : hardness * .05F;
            if (cost > 0) {
                impact.hadBlock = true; float strength = (float) impact.strength(); if (strength < cost) cost = (strength + cost) / 2;
                cost = Math.min(strength, cost); wallDamage += cost * 4;
                state.entityInside(level, pos, entity, InsideBlockEffectApplier.NOOP, false);
                if (!glass && entity instanceof LivingEntity living && SourceEraMaterials.glass(state) && living.getRandom().nextFloat() < StandBlockShardEntity.bleedingChance(living)) glass = true;
                if (SourceEraMaterials.material(state) == SourceEraMaterials.Material.CACTUS) hurt(level, entity, level.damageSources().cactus(), 1, impact.attacker);
                if (entity.isOnFire() && state.is(Blocks.TNT)) {
                    RestorationHooks.remember(level, pos, state, level.getBlockEntity(pos), List.of());
                    TntBlock.prime(level, pos, entity instanceof LivingEntity living ? living : null, ItemStack.EMPTY); level.removeBlock(pos, false);
                }
                remaining -= Math.max(cost, .05F);
            }
            if (remaining <= 0) break;
        }
        var stopped = movement.subtract(actual); var face = Direction.getApproximateNearest(stopped.x, stopped.y, stopped.z);
        if (face != Direction.DOWN) {
            if (level.getGameRules().get(JojoGameRules.BREAK_BLOCKS) && impact.radius > 0) {
                var bounds = entity.getBoundingBox();
                var center = new Vec3(lerp(face.getStepX() * .5 + .5, bounds.minX, bounds.maxX), lerp(face.getStepY() * .5 + .5, bounds.minY, bounds.maxY), lerp(face.getStepZ() * .5 + .5, bounds.minZ, bounds.maxZ));
                impact.destroyed = explode(level, impact.attacker, center, movement, impact.radius, 0, entity, true, false, 0, 0);
                if (glass && entity instanceof LivingEntity living) StandBlockShardEntity.bleed(living);
            }
            if (wallDamage > 0) hurt(level, entity, level.damageSources().flyIntoWall(), wallDamage, impact.attacker);
        }
        impact.reset(); entity.setAttached(SAVED, impact.save());
    }
    private static double collideAxis(Direction.Axis axis, AABB box, double desired, List<Entity> potential, List<Entity> collided) {
        if (Math.abs(desired) < 1E-7) return 0;
        double offset = desired;
        for (var target : potential) { double result = Shapes.create(target.getBoundingBox()).collide(axis, box, offset); if (result != desired) { collided.add(target); offset = result; } }
        return desired; // Original deliberately returns the requested offset, not the entity-clipped offset.
    }
    private static double manhattan(AABB a, AABB b) { return Math.max(Math.max(a.minX - b.maxX, b.minX - a.maxX), 0) + Math.max(Math.max(a.minY - b.maxY, b.minY - a.maxY), 0) + Math.max(Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ), 0); }
    private static double lerp(double fraction, double a, double b) { return a + fraction * (b - a); }
    private static void hurt(ServerLevel level, Entity target, DamageSource source, float damage, LivingEntity attacker) {
        if (target instanceof LivingEntity living) StandDamage.hurt(level, living, source, damage); else target.hurtServer(level, source, damage);
        if (source.getDirectEntity() != attacker && attacker instanceof StandManifestation stand && stand.owner() != null && target instanceof LivingEntity living)
            ProgressionService.onAttack(living, new DamageSource(level.registryAccess().getOrThrow(DamageTypes.MOB_ATTACK), attacker, stand.owner()), damage);
    }
    private record Drop(ItemStack stack, BlockPos pos) { }
    private static List<BlockPos> explode(ServerLevel level, LivingEntity attacker, Vec3 center, Vec3 direction, float radius, float damage,
            Entity excluded, boolean breakBlocks, boolean shards, double strength, double precision) {
        var blocks = new HashSet<BlockPos>(); direction = direction.normalize();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) continue;
            var ray = new Vec3(x / 15F * 2F - 1F, y / 15F * 2F - 1F, z / 15F * 2F - 1F).normalize(); if (ray.dot(direction) < 0) continue;
            var position = center; float power = radius * (.7F + level.getRandom().nextFloat() * .6F);
            for (; power > 0; power -= .225F, position = position.add(ray.scale(.3))) {
                var pos = BlockPos.containing(position); var state = level.getBlockState(pos); var fluid = level.getFluidState(pos);
                if (!state.isAir() || !fluid.isEmpty()) power -= (Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance()) + .3F) * .3F;
                if (power > 0 && !state.is(Blocks.SPAWNER)) blocks.add(pos);
            }
        }
        double diameter = radius * 2;
        if (diameter > 0) for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center.subtract(diameter + 1, diameter + 1, diameter + 1), center.add(diameter + 1, diameter + 1, diameter + 1)), e -> e != excluded && canHarm(attacker, e))) {
            double distance = Math.sqrt(target.distanceToSqr(center)) / diameter; if (distance > 1) continue;
            var toward = target.getEyePosition().subtract(center).normalize(); double push = (1 - distance) * ServerExplosion.getSeenPercent(center, target);
            var source = new DamageSource(level.registryAccess().getOrThrow(DamageTypes.EXPLOSION), attacker, user(attacker));
            hurt(level, target, source, damage * (attacker instanceof StandManifestation ? StandConfig.server().standPowerMultiplier() : 1), attacker);
            target.setDeltaMovement(target.getDeltaMovement().add(toward.scale(push))); target.syncVelocity = true;
        }
        var destroyed = new ArrayList<BlockPos>(); var drops = new ArrayList<Drop>(); var brokenVisuals = new ArrayList<BlockVisual>();
        int bMinX = 30000001, bMinY = 999, bMinZ = 30000001, bMaxX = -30000001, bMaxY = -999, bMaxZ = -30000001;
        if (breakBlocks) for (var pos : blocks) {
            if (!allowed(level, pos, attacker)) continue; var state = level.getBlockState(pos); var be = level.getBlockEntity(pos);
            var fragments = new ArrayList<StandBlockShardEntity>();
            if (shards && SourceEraMaterials.hard(state)) for (int i = 0; i < 3; i++) {
                var shard = new StandBlockShardEntity(level, attacker, state, pos); shard.setPos(pos.getX() + level.getRandom().nextDouble(), pos.getY() + level.getRandom().nextDouble(), pos.getZ() + level.getRandom().nextDouble());
                double inaccuracy = Math.min(Math.max(100 - precision * 4.5, 0) * .0075, 1);
                var away = shard.position().subtract(center).normalize(); var shoot = direction.lerp(away, inaccuracy);
                shard.shoot(shoot.x, shoot.y, shoot.z, .5F + (float) strength * .05F, 4); fragments.add(shard);
            }
            if (!(user(attacker) instanceof Player p && p.isCreative())) {
                for (var stack : Block.getDrops(state, level, pos, be, attacker, ItemStack.EMPTY)) {
                    for (var drop : drops) { if (ItemEntity.areMergable(drop.stack, stack)) { int count = Math.min(16 - drop.stack.getCount(), stack.getCount()); if (count > 0) { drop.stack.grow(count); stack.shrink(count); } } if (stack.isEmpty()) break; }
                    if (!stack.isEmpty()) drops.add(new Drop(stack, pos));
                }
            } else RestorationHooks.remember(level, pos, state, be, List.of());
            if (level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3)) destroyed.add(pos);
            if (!(state.getBlock() instanceof BaseFireBlock)) {
                bMinX = Math.min(bMinX, pos.getX()); bMinY = Math.min(bMinY, pos.getY()); bMinZ = Math.min(bMinZ, pos.getZ());
                bMaxX = Math.max(bMaxX, pos.getX()); bMaxY = Math.max(bMaxY, pos.getY()); bMaxZ = Math.max(bMaxZ, pos.getZ());
                brokenVisuals.add(new BlockVisual(pos.immutable(), Block.getId(state)));
            }
            fragments.forEach(level::addFreshEntity);
            var history = BlockHistory.at(level, pos); var record = history == null ? null : history.getBrokenBlockAt(pos);
            if (record != null && !fragments.isEmpty()) record.withEntities(fragments.toArray(StandBlockShardEntity[]::new));
        }
        drops.forEach(drop -> Block.popResource(level, drop.pos, drop.stack));
        sendVisuals(level, brokenVisuals, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ);
        // The source's active test=true includes the entire remaining cube, not a spherical shell.
        var shockwaveVisuals = new ArrayList<BlockVisual>();
        int sMinX = (int) Math.floor(center.x - radius), sMinY = (int) Math.floor(center.y - radius), sMinZ = (int) Math.floor(center.z - radius);
        int sMaxX = (int) Math.ceil(center.x + radius), sMaxY = (int) Math.ceil(center.y + radius), sMaxZ = (int) Math.ceil(center.z + radius);
        for (var pos : BlockPos.betweenClosed(new BlockPos(sMinX, sMinY, sMinZ), new BlockPos(sMaxX, sMaxY, sMaxZ))) {
            var state = level.getBlockState(pos); if (!state.isAir()) shockwaveVisuals.add(new BlockVisual(pos.immutable(), Block.getId(state)));
        }
        sendVisuals(level, shockwaveVisuals, sMinX, sMinY, sMinZ, sMaxX, sMaxY, sMaxZ);
        return destroyed;
    }
    private static void sendVisuals(ServerLevel level, List<BlockVisual> visuals, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (visuals.isEmpty()) return;
        int size = visuals.size();
        if (size > 256) {
            for (int i = 0; i < 256; i++) {
                int index = i + level.getRandom().nextInt(size - i);
                var tmp = visuals.get(index); visuals.set(index, visuals.get(i)); visuals.set(i, tmp);
            }
            visuals = visuals.subList(0, 256);
        }
        var payload = new ShockwavePayload(List.copyOf(visuals));
        for (var player : level.players()) {
            double x = player.getX(), y = player.getY(), z = player.getZ();
            double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
            double dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
            double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
            if (dx * dx + dy * dy + dz * dz < 64 * 64) ServerPlayNetworking.send(player, payload);
        }
    }
    private StandHeavyImpact() { }
}
