package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Original base actions, with one authoritative hold and a separate continuous heavy-punch clock. */
public final class PillarmanBaseActions {
    public enum Held { NONE, ABSORPTION, STONE_FORM, SENSES, EVASION, AGILITY }
    /** Later owner-bound projectiles implement the original ModdedProjectileEntity evasion contract. */
    public interface EvasionProjectile { boolean canBeEvaded(LivingEntity target); boolean standDamage(); }
    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();
    private static final net.minecraft.resources.Identifier SPEED = RipplesFabric.id("pillarman_action_speed");
    private static final class Runtime {
        Held held = Held.NONE;
        int heldTicks, heartbeat, punchTicks, regenerationCooldown, evasionCooldown, leapCooldown;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        boolean countering;
    }
    private static Runtime runtime(ServerPlayer player) { return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime()); }
    public static Held held(ServerPlayer player) { var runtime = RUNTIME.get(player.getUUID()); return runtime == null ? Held.NONE : runtime.held; }
    public static int heldTicks(ServerPlayer player) { var runtime = RUNTIME.get(player.getUUID()); return runtime == null ? 0 : runtime.heldTicks; }
    public static int punchTicks(ServerPlayer player) { var runtime = RUNTIME.get(player.getUUID()); return runtime == null ? 0 : runtime.punchTicks; }
    public static void clearAll() { RUNTIME.clear(); }
    public static void clear(LivingEntity entity) {
        RUNTIME.remove(entity.getUUID()); entity.setAttached(PillarmanAttachments.SENSES_TICKS, 0); entity.setAttached(PillarmanAttachments.ACTIONS, PillarmanAttachments.Actions.EMPTY);
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SPEED);
    }
    public static void keepHeld(ServerPlayer player) { var runtime = RUNTIME.get(player.getUUID()); if (runtime != null) runtime.heartbeat = 0; }
    public static void stopHeld(ServerPlayer player) {
        var runtime = RUNTIME.get(player.getUUID()); if (runtime == null) return;
        if (runtime.held == Held.EVASION) runtime.evasionCooldown = player.isCreative() ? 0 : Math.min(50, runtime.heldTicks);
        runtime.held = Held.NONE; runtime.heldTicks = 0; runtime.heartbeat = 0;
        player.setAttached(PillarmanAttachments.SENSES_TICKS, 0); movement(player, runtime);
    }
    public static boolean canAct(ServerPlayer player) {
        return PillarmanService.hasPillarman(player) && player.isAlive() && !player.isSpectator() && !TimeStopService.isFrozen(player);
    }
    /** Call before a later mode's start; failed validation must never cancel the current hold. */
    public static boolean canStart(ServerPlayer player, int minimumStage, boolean stoneAllowed, boolean ignoreStun) {
        return canContinue(player, minimumStage, stoneAllowed, ignoreStun)
                && !com.github.standobyte.jojo.fabric.pillarman.PossessionService.isHolding(player)
                && !com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.isHolding(player)
                && !com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.active(player)
                && !com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.active(player);
    }
    public static boolean canContinue(ServerPlayer player, int minimumStage, boolean stoneAllowed, boolean ignoreStun) {
        if (!canAct(player) || PillarmanService.state(player).stage() < minimumStage
                || !stoneAllowed && PillarmanService.state(player).stoneForm() || !ignoreStun && VampireCombatService.isStunned(player)) return false;
        var runtime = runtime(player);
        return runtime.punchTicks == 0 && (runtime.held == Held.NONE || runtime.held == Held.ABSORPTION);
    }
    public static boolean receive(ServerPlayer player, PillarmanActionPayload payload) {
        return switch (payload.action()) {
            case KEEP_HELD -> { keepHeld(player); com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.keepHeld(player); com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.keepHeld(player); yield true; }
            case STOP_HELD, RELEASE_HELD -> { stopHeld(player); com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.release(player, payload.action() == PillarmanActionPayload.Action.RELEASE_HELD); com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.release(player, payload.action() == PillarmanActionPayload.Action.RELEASE_HELD); yield true; }
            case HEAVY_PUNCH -> heavyPunch(player);
            case START_ABSORPTION -> startHeld(player, Held.ABSORPTION);
            case START_STONE_FORM -> startHeld(player, Held.STONE_FORM);
            case REGENERATION -> regenerate(player);
            case START_SENSES -> startHeld(player, Held.SENSES);
            case START_EVASION -> startHeld(player, Held.EVASION);
            case START_AGILITY -> startHeld(player, Held.AGILITY);
            case LEAP -> leap(player);
            case HORN -> com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.horn(player);
            case RIBS -> com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts.ribs(player);
            case ERRATIC_BLAZE -> com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.erratic(player);
            case START_GIANT_HEAT -> com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.giant(player);
            case START_DETONATION -> com.github.standobyte.jojo.fabric.pillarman.PillarHeatActions.detonation(player);
            case START_LIGHT_FLASH -> com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.flash(player);
            case START_LIGHT_DECOY -> com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.decoy(player);
            case BLADE_SLASH -> com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.slash(player);
            case START_BLADE_DASH -> com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.dashCharge(player);
            case START_BLADE_BARRAGE -> com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.barrage(player);
        };
    }
    public static boolean heavyPunch(ServerPlayer player) {
        if (!canStart(player, 1, false, false) || !player.getMainHandItem().isEmpty()
                || !player.isCreative() && PillarmanService.state(player).energy() < 10) return false;
        // Registered energyCost(10) checks eligibility; original consumeEnergy() is empty.
        var runtime = runtime(player); runtime.punchTicks = 1; runtime.dimension = player.level().dimension(); movement(player, runtime); return true;
    }
    public static boolean startHeld(ServerPlayer player, Held held) {
        if (held == Held.NONE) return false;
        boolean stoneAllowed = held == Held.ABSORPTION || held == Held.STONE_FORM || held == Held.SENSES;
        if (!canStart(player, held == Held.STONE_FORM ? 1 : 2, stoneAllowed, stoneAllowed)) return false;
        var runtime = runtime(player);
        if (held == runtime.held || held == Held.EVASION && runtime.evasionCooldown > 0
                || held == Held.ABSORPTION && (!player.getMainHandItem().isEmpty() || player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
                || !player.isCreative() && PillarmanService.state(player).energy() < holdCost(held)) return false;
        stopHeld(player); runtime.held = held; runtime.dimension = player.level().dimension(); movement(player, runtime); return true;
    }
    public static boolean regenerate(ServerPlayer player) {
        if (!canStart(player, 2, true, true) || runtime(player).regenerationCooldown > 0 || !PillarmanService.spend(player, 40)) return false;
        int duration = 60;
        var old = player.getEffect(MobEffects.REGENERATION);
        if (old != null) {
            int oldGap = 50 >> old.getAmplifier(), newGap = 50 >> 4;
            if (oldGap > 0) {
                int oldAppliesIn = old.getDuration() % oldGap, newAppliesIn = duration % newGap;
                if (newAppliesIn < oldAppliesIn) {
                    int adjusted = duration + oldAppliesIn - newAppliesIn;
                    while (adjusted > duration) adjusted -= newGap;
                    if (adjusted > 0) duration = adjusted;
                } else duration -= newAppliesIn - oldAppliesIn;
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 4, false, false, true));
        runtime(player).regenerationCooldown = player.isCreative() ? 0 : 20;
        movement(player, runtime(player));
        PillarmanService.sound(player, "pillar_man_strong_regen", 1.5F, 1.2F); return true;
    }
    public static boolean absorb(ServerPlayer player, LivingEntity victim) {
        if (!canAct(player) || PillarmanService.state(player).stage() < 2 || !player.getMainHandItem().isEmpty()
                || player.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || victim == player || !victim.isAlive()
                || victim.isSpectator() || victim.level() != player.level() || target(player, 2) != victim) return false;
        var type = player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, RipplesFabric.id("pillarman_absorption")));
        if (HamonCharges.protectFromContact(victim, new DamageSource(type, player), 2)) return false;
        // Null attacker matches DamageUtil.dealPillarmanAbsorptionDamage(..., null), including ordinary hit immunity.
        if (!victim.hurtServer(player.level(), new DamageSource(type), 2)) return false;
        for (var effect : java.util.List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA)) {
            var old = victim.getEffect(effect); victim.addEffect(new MobEffectInstance(effect, (old == null ? 0 : old.getDuration()) + 40, 1));
        }
        PillarmanService.addEnergy(player, 35 * VampireConfig.server().bloodDrainMultiplier()[player.level().getDifficulty().getId()]);
        return true;
    }
    public static boolean leap(ServerPlayer player) {
        if (!canStart(player, 1, false, false) || runtime(player).leapCooldown > 0
                || !(player.onGround() || player.getVehicle() != null && player.getVehicle().onGround())) return false;
        float strength = PillarmanService.state(player).leapStrength();
        Vec3 velocity = Vec3.directionFromRotation(Math.min(player.getXRot(), -30F), player.getYRot()).scale(strength);
        Entity leaper = player.getVehicle() == null ? player : player.getVehicle();
        leaper.setOnGround(false); if (leaper instanceof LivingEntity living) living.setJumping(true);
        leaper.needsSync = true; leaper.setDeltaMovement(velocity.x, velocity.y * .5, velocity.z); leaper.syncVelocity = true; player.setShiftKeyDown(false);
        var ground = player.level().getBlockState(player.getOnPos());
        if (!ground.isAir()) {
            player.level().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                    player.getX(), player.getY(), player.getZ(), (int) (150 * Math.min(.2F + strength / 3, 2.5F)), 0, 0, 0, .15);
            var sound = ground.getSoundType();
            player.level().playSound(null, player.blockPosition(), sound.getBreakSound(), net.minecraft.sounds.SoundSource.PLAYERS, sound.getVolume() * .25F * strength, sound.getPitch() * .75F);
        }
        runtime(player).leapCooldown = player.isCreative() ? 0 : 20; movement(player, runtime(player)); return true;
    }
    public static void tick(ServerPlayer player) {
        var runtime = RUNTIME.get(player.getUUID()); if (runtime == null) return;
        if (!canAct(player)) { if (!TimeStopService.isFrozen(player)) clear(player); return; }
        if (runtime.dimension != null && runtime.dimension != player.level().dimension()) { clear(player); return; }
        if (runtime.regenerationCooldown > 0) runtime.regenerationCooldown--;
        if (runtime.evasionCooldown > 0) runtime.evasionCooldown--;
        if (runtime.leapCooldown > 0) runtime.leapCooldown--;
        if (runtime.punchTicks > 0) {
            if (PillarmanService.state(player).stoneForm() || VampireCombatService.isStunned(player) || !player.getMainHandItem().isEmpty()) runtime.punchTicks = 0;
            else {
                int tick = runtime.punchTicks++;
                if (tick == 3) { player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); PillarmanService.sound(player, "pillar_man_swing", 1, 1.25F); }
                if (tick == 5) punchHit(player);
                if (tick >= 8) runtime.punchTicks = 0;
            }
        }
        if (runtime.held != Held.NONE) {
            if (++runtime.heartbeat > 15 || PillarmanService.state(player).stage() < (runtime.held == Held.STONE_FORM ? 1 : 2)
                    || (runtime.held == Held.EVASION || runtime.held == Held.AGILITY) && (PillarmanService.state(player).stoneForm() || VampireCombatService.isStunned(player))
                    || runtime.held == Held.ABSORPTION && !player.getMainHandItem().isEmpty()
                    || !PillarmanService.spend(player, holdCost(runtime.held))) stopHeld(player);
            else {
                runtime.heldTicks++;
                switch (runtime.held) {
                    case ABSORPTION -> { var target = target(player, 2); if (target != null) absorb(player, target); }
                    case STONE_FORM -> { if (runtime.heldTicks >= 40) { PillarmanService.setStoneForm(player, !PillarmanService.state(player).stoneForm()); PillarmanService.setBlades(player, false); stopHeld(player); } }
                    case SENSES -> player.setAttached(PillarmanAttachments.SENSES_TICKS, runtime.heldTicks);
                    case EVASION -> { if (runtime.heldTicks >= 50) stopHeld(player); }
                    default -> { }
                }
            }
        }
        movement(player, runtime);
    }
    public static double sensesRadius(LivingEntity player) { return Math.min(player.getAttachedOrCreate(PillarmanAttachments.SENSES_TICKS) * .5, 36); }
    public static boolean sensesScanDue(LivingEntity player) { int ticks = player.getAttachedOrCreate(PillarmanAttachments.SENSES_TICKS); return ticks > 0 && (ticks < 160 || ticks % 20 == 0); }
    private static float holdCost(Held held) { return switch (held) { case SENSES -> .05F; case EVASION -> 1.5F; case AGILITY -> 1; default -> 0; }; }
    private static void movement(ServerPlayer player, Runtime runtime) {
        var visual = new PillarmanAttachments.Actions(runtime.held.ordinal(), runtime.heldTicks, runtime.punchTicks, runtime.regenerationCooldown, runtime.evasionCooldown, runtime.leapCooldown);
        if (!visual.equals(player.getAttachedOrCreate(PillarmanAttachments.ACTIONS))) player.setAttached(PillarmanAttachments.ACTIONS, visual);
        double speed = switch (runtime.held) { case STONE_FORM, SENSES -> .5; case EVASION -> 5; case AGILITY -> 0; default -> 1; };
        if (runtime.punchTicks > 0) speed = Math.min(speed, .5);
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED); if (attribute == null) return;
        var old = attribute.getModifier(SPEED);
        if (old != null && old.amount() == speed - 1) return;
        attribute.removeModifier(SPEED);
        if (speed != 1) attribute.addTransientModifier(new AttributeModifier(SPEED, speed - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static boolean evade(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !canAct(player) || PillarmanService.state(player).stoneForm() || VampireCombatService.isStunned(player)) return false;
        var runtime = RUNTIME.get(player.getUUID());
        if (runtime == null || runtime.countering || runtime.held != Held.EVASION && runtime.held != Held.AGILITY || runtime.heartbeat > 15) return false;
        Entity attacker = source.getDirectEntity();
        if (source.is(DamageTypeTags.IS_EXPLOSION) || !(attacker instanceof LivingEntity || attacker instanceof Projectile || attacker instanceof EvasionProjectile)) return false;
        boolean canSeeStands = player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()
                || player.getActiveEffects().stream().anyMatch(effect -> net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).equals(RipplesFabric.id("spirit_vision")));
        if (attacker instanceof StandManifestation && !canSeeStands) return false;
        if (attacker instanceof EvasionProjectile projectile) return projectile.canBeEvaded(player) && (!projectile.standDamage() || canSeeStands);
        if (runtime.held == Held.AGILITY && attacker instanceof LivingEntity living && !(attacker instanceof StandManifestation) && player.getRandom().nextFloat() < .3F) {
            runtime.countering = true;
            try { living.hurtServer(player.level(), player.damageSources().playerAttack(player), barehandDamage(player) * .75F); }
            finally { runtime.countering = false; }
        }
        PillarmanService.sound(player, "pillar_man_evasion", 1, 1); return true;
    }
    private static float barehandDamage(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE); if (attribute == null) return 0;
        var excluded = new java.util.HashSet<net.minecraft.resources.Identifier>();
        var item = entity.getMainHandItem().get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (item != null) for (var entry : item.modifiers()) if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) excluded.add(entry.modifier().id());
        double base = attribute.getBaseValue();
        for (var modifier : attribute.getModifiers()) if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
        double value = base;
        for (var modifier : attribute.getModifiers()) if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += base * modifier.amount();
        for (var modifier : attribute.getModifiers()) if (!excluded.contains(modifier.id()) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1 + modifier.amount();
        return (float) Math.max(0, value);
    }
    private static void punchHit(ServerPlayer player) {
        var target = target(player, 8);
        if (target != null) {
            var source = player.damageSources().playerAttack(player); float amount = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4;
            if (target.hurtServer(player.level(), source, amount)) {
                PillarmanService.sound(player, "pillar_man_punch", 1.2F, .8F);
                target.knockback(2, player.getX() - target.getX(), player.getZ() - target.getZ(), source, amount); target.syncVelocity = true;
                VampireCombatService.onClawHit(target, player);
            }
        } else {
            var hit = player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(10)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                destroyBlock(player, hit.getBlockPos());
                if (PillarmanService.state(player).stage() > 1) for (var direction : net.minecraft.core.Direction.values()) destroyBlock(player, hit.getBlockPos().relative(direction));
                PillarmanService.sound(player, "heavy_punch", 1.5F, 1.2F);
            }
        }
    }
    private static void destroyBlock(ServerPlayer player, BlockPos pos) {
        if (!player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS) || !player.mayBuild() || !player.level().mayInteract(player, pos)) return;
        var block = player.level().getBlockState(pos); if (block.isAir()) return;
        float hardness = block.getDestroySpeed(player.level(), pos); if (hardness < 0) return;
        double duration = player.isCreative() ? 0 : hardness / (player.getDestroySpeed(block) / 2);
        if (!player.hasCorrectToolForDrops(block)) duration /= 3;
        if (duration <= 2.5 * Math.sqrt(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                && net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, block, player.level().getBlockEntity(pos))) player.level().destroyBlock(pos, !player.isCreative(), player);
    }
    /** Uses all source living targets, including undead; vampire feeding exclusions do not apply. */
    private static LivingEntity target(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double distance = block.getType() == HitResult.Type.MISS ? range * range : start.distanceToSqr(block.getLocation());
        LivingEntity result = null;
        for (var entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1), e -> e != player && e.isAlive() && !e.isSpectator())) {
            var bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit = bounds.contains(start) ? java.util.Optional.of(start) : bounds.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= distance) { result = entity; distance = start.distanceToSqr(hit.get()); }
        }
        return result;
    }
    private PillarmanBaseActions() { }
}
