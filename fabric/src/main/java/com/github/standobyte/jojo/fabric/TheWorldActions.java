package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.mixin.TimeSkipMobAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Original TimeStopInstant behavior shared by Star Platinum and The World, validated on the server. */
public final class TheWorldActions {
    public static final int COOLDOWN_BLINK = 11, COOLDOWN_TIME_PUNCH = 12;
    private static final float BLINK_COST = 180;
    public static float tickingCost(ServerPlayer player) { return 720F / ProgressionService.timeStopTicks(player); }

    public static boolean blink(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (!progress.hasStand() || !ProgressionService.require(player, progress.timeStopUnlock(), "key.jojo.fabric.blink")) return false;
        // Original requirements check the instant action's own cooldown. A full Time Stop's longer
        // cooldown is independent; its instant variation becomes usable after one sixth that time.
        if (AbilityCooldowns.remaining(player, COOLDOWN_BLINK) > 0 || TimeStopService.isFrozen(player)
                || TimeStopService.isCharging(player) || TimeStopService.isActive(player)) return false;
        var stand = StandService.current(player);
        if (stand != null && stand.actionPhase() != 0) return false;
        boolean ignoreDebuff = player.isCreative() || ProgressionService.state(player).isBurst();
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
        if (!(speed > 0) || !Double.isFinite(speed)) return false;
        float perTick = tickingCost(player);
        int budget = ProgressionService.timeStopTicks(player);
        if (!ignoreDebuff) {
            // Preserve StandAction.onPerform's upfront debit before getMaxImpliedTicks subtracts
            // the base cost again. The original always allows at least five implied ticks.
            float afterUpfront = Math.max(0, progress.stamina() - BLINK_COST);
            budget = Math.clamp((int) Math.floor((afterUpfront - BLINK_COST) / perTick), 5, budget);
        }
        Vec3 from = player.position();
        var target = destination(player, Math.min(192, budget * speed), progress.hasTheWorld());
        Vec3 destination = safeBlinkTravel(player, target.position());
        int elapsed = Math.clamp((int) Math.ceil(from.distanceTo(destination) / speed), 0, budget);
        float yaw = player.getYRot();
        if (target.entity() != null) {
            Vec3 facing = target.entity().position().subtract(destination);
            yaw = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
        }
        forgetLostTargets(player, destination);
        if (!player.isCreative()) {
            // Original consumeStamina clamps to zero when the minimum five ticks exceed reserves.
            player.setAttached(StandAttachments.PROGRESS,
                    progress.withStamina(Math.max(0, progress.stamina() - BLINK_COST - elapsed * perTick)));
        }
        player.connection.teleport(destination.x, destination.y, destination.z, yaw, player.getXRot());
        player.yRotO = player.getYRot();
        if (stand != null) {
            stand.translateControlAnchor(destination.subtract(from));
            stand.setPos(stand.position().add(destination.subtract(from)));
            stand.playOriginal(progress.hasTheWorld() ? "the_world_time_stop_blink" : "star_platinum_time_stop_blink");
        } else {
            var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(
                    RipplesFabric.id(progress.hasTheWorld() ? "the_world_time_stop_blink" : "star_platinum_time_stop_blink"));
            if (sound != null) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                    net.minecraft.sounds.SoundSource.AMBIENT, 5, 1);
        }
        skipTicks(player, stand, elapsed);
        int cooldown = player.isCreative() ? 0 : (int) (elapsed * (ProgressionService.state(player).isBurst() ? 1F : 3F) / 6F);
        AbilityCooldowns.start(player, COOLDOWN_BLINK, cooldown);
        if (TimeStopService.cooldownRemaining(player) == 0) TimeStopService.setCooldown(player, cooldown);
        // Instant travel does not award Time Stop learning (commented out in the original perform()).
        return true;
    }

    private record BlinkTarget(Vec3 position, LivingEntity entity) { }

    /** TimeStopInstant.calcBlinkPos: entities, block top/face, or a ray miss projected onto ground. */
    private static BlinkTarget destination(ServerPlayer player, double distance, boolean behind) {
        var level = player.level();
        Vec3 start = player.getEyePosition(), look = player.getLookAngle();
        // Ray casting may read chunks, so bound the ray before asking the level for block shapes.
        double loadedDistance = 0;
        for (int step = 1, steps = Math.max(1, (int) Math.ceil(distance)); step <= steps; step++) {
            double next = distance * step / steps;
            if (!level.hasChunkAt(BlockPos.containing(start.add(look.scale(next))))) break;
            loadedDistance = next;
        }
        distance = loadedDistance;
        Vec3 end = start.add(look.scale(distance));
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? distance : start.distanceTo(block.getLocation());
        LivingEntity target = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1),
                entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                        && !(entity instanceof StarPlatinumEntity other && other.ownerId() == player.getId()))) {
            var box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var hit = box.clip(start, end);
            double hitDistance = box.contains(start) ? 0 : hit.map(start::distanceTo).orElse(Double.POSITIVE_INFINITY);
            if (hitDistance < nearest) { nearest = hitDistance; target = candidate; }
        }
        if (target != null) {
            double separation = target.getBbWidth() + player.getBbWidth();
            Vec3 position = behind
                    ? target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(separation))
                    : player.distanceToSqr(target) > separation * separation
                            ? target.position().subtract(look.scale(separation)) : player.position();
            return new BlinkTarget(position, target);
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            var pos = block.getBlockPos();
            var landing = level.isEmptyBlock(pos.above()) ? pos.above() : pos.relative(block.getDirection());
            return new BlinkTarget(Vec3.atBottomCenterOf(landing), null);
        }
        // Respect modern dimension minimum Y while retaining the original no-ground fallback.
        BlockPos ground = BlockPos.containing(end);
        while (ground.getY() > level.getMinY() && level.hasChunkAt(ground.below()) && level.isEmptyBlock(ground.below())) {
            ground = ground.below();
        }
        double y = ground.getY() > level.getMinY() && level.hasChunkAt(ground) ? ground.getY() : player.getY();
        return new BlinkTarget(new Vec3(end.x, y, end.z), null);
    }

    private static void forgetLostTargets(ServerPlayer player, Vec3 destination) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8),
                entity -> entity.getTarget() == player
                        && entity.getLookAngle().dot(entity.getEyePosition().subtract(destination)) >= 0)) {
            mob.setTarget(null);
            ((TimeSkipMobAccessor) mob).jojo$targetSelector().getAvailableGoals().stream()
                    .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        }
    }

    /** Original skipTicks only advances age; it never executes AI, physics, damage, effects or cooldown ticks. */
    public static void skipTicks(ServerPlayer player, StarPlatinumEntity stand, int ticks) {
        if (ticks < 0) return;
        player.tickCount += ticks;
        if (stand != null) stand.tickCount += ticks;
        StandTimeSkipPayload.send(player, stand, ticks);
    }

    /** Block-top placement can move up then across, without sweeping a player through the block's side. */
    private static Vec3 safeBlinkTravel(Entity entity, Vec3 destination) {
        Vec3 start = entity.position();
        if (destination.y > start.y + 0.01) {
            Vec3 raised = new Vec3(start.x, destination.y, start.z);
            Vec3 safeUp = safeTravel(entity, start, raised);
            if (safeUp.distanceToSqr(raised) < 1.0E-8) return safeTravel(entity, raised, destination);
        }
        return safeTravel(entity, start, destination);
    }

    /** Stops at the first blocked/unloaded/out-of-bounds full-body position; never tunnels to a clear endpoint. */
    static Vec3 safeTravel(Entity entity, Vec3 destination) { return safeTravel(entity, entity.position(), destination); }

    private static Vec3 safeTravel(Entity entity, Vec3 start, Vec3 destination) {
        var level = entity.level();
        Vec3 delta = destination.subtract(start);
        if (!Double.isFinite(delta.lengthSqr())) return start;
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.15));
        Vec3 safe = start;
        for (int i = 1; i <= steps; i++) {
            Vec3 next = start.add(delta.scale((double) i / steps));
            var box = entity.getBoundingBox().move(next.subtract(entity.position()));
            if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)
                    || !loaded(level, box) || !level.noCollision(entity, box)) break;
            safe = next;
        }
        return safe;
    }

    private static boolean loaded(net.minecraft.world.level.Level level, AABB box) {
        return level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX - 1.0E-7, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ - 1.0E-7))
                && level.hasChunkAt(BlockPos.containing(box.maxX - 1.0E-7, box.minY, box.maxZ - 1.0E-7));
    }

    public static boolean timePunch(ServerPlayer player) {
        var stand = StandService.current(player);
        return stand != null && stand.beginTimePunch();
    }
    public static int blinkCooldown(ServerPlayer player) { return AbilityCooldowns.remaining(player, COOLDOWN_BLINK); }
    public static int timePunchCooldown(ServerPlayer player) { return AbilityCooldowns.remaining(player, COOLDOWN_TIME_PUNCH); }
    private TheWorldActions() { }
}
