package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/** Original claw's nonexplosive KnockbackCollisionImpact: entity collisions and wall damage. */
public final class VampireCollisionImpact {
    private static final Map<LivingEntity, Impact> IMPACTS = new IdentityHashMap<>();
    private static final class Impact {
        final ServerPlayer attacker; final Vec3 direction; double strength, minCos = 1;
        Impact(LivingEntity target, ServerPlayer attacker) {
            this.attacker = attacker; var velocity = target.getDeltaMovement(); direction = velocity.normalize();
            strength = velocity.length() * (1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        }
        double strength() { return strength * minCos; }
    }
    public static void start(LivingEntity target, ServerPlayer attacker) {
        var impact = new Impact(target, attacker); if (impact.strength > 0) {
            com.github.standobyte.jojo.fabric.stand.StandHeavyImpact.clear(target);
            IMPACTS.put(target, impact);
        }
    }
    public static void tick() {
        IMPACTS.entrySet().removeIf(entry -> {
            var target = entry.getKey(); var impact = entry.getValue(); var velocity = target.getDeltaMovement();
            if (target.isRemoved() || !target.isAlive()) return true;
            if (TimeStopService.isFrozen(target)) return false;
            if (Math.abs(velocity.x) < 1E-7 && Math.abs(velocity.z) < 1E-7) return true;
            double cos = velocity.normalize().dot(impact.direction); if (cos <= 0) return true;
            impact.minCos = Math.min(impact.minCos, cos); impact.strength = Math.min(impact.strength, velocity.length()); return impact.strength <= 0;
        });
    }
    /** Called at vanilla collision resolution, while the entity still occupies its pre-move box. */
    public static void collide(Entity entity, Vec3 movement, Vec3 actual) {
        if (!(entity instanceof LivingEntity living) || !(entity.level() instanceof ServerLevel level)) return;
        var impact = IMPACTS.get(living); if (impact == null || movement.lengthSqr() < 1E-7 || TimeStopService.isFrozen(entity)) return;
        AABB original = entity.getBoundingBox().inflate(.25), swept = original.expandTowards(movement);
        for (var other : level.getEntitiesOfClass(LivingEntity.class, swept, target -> target != entity && target.isAlive() && target.isPickable())) {
            if (other instanceof Player player && (player.isCreative() || player.isSpectator() || !impact.attacker.canHarmPlayer(player))) continue;
            if (other.isAlliedTo(entity) || other.isAlliedTo(impact.attacker)) continue;
            if (other.getBoundingBox().inflate(original.getXsize() / 2, original.getYsize() / 2, original.getZsize() / 2)
                    .clip(original.getCenter(), original.getCenter().add(movement)).isEmpty()) continue;
            float damage = (float) impact.strength() * 5;
            var source = new DamageSource(level.registryAccess().getOrThrow(VampireCombatService.COLLISION_DAMAGE), entity);
            StandDamage.hurt(level, other, source, damage);
            other.knockback(impact.strength(), -movement.x, -movement.z, source, damage); other.syncVelocity = true;
        }
        if (movement.equals(actual)) return;
        Vec3 stopped = movement.subtract(actual);
        // Original does not apply collision damage for a landing against the floor.
        if (Math.abs(stopped.y) > Math.max(Math.abs(stopped.x), Math.abs(stopped.z)) && stopped.y < 0) return;
        float damage = 0; double remaining = impact.strength();
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            var state = level.getBlockState(pos); var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() || !shape.bounds().move(pos).intersects(swept) || shape.bounds().move(pos).intersects(entity.getBoundingBox().deflate(1E-7))) continue;
            float hardness = state.getDestroySpeed(level, pos); if (!state.requiresCorrectToolForDrops()) hardness *= .6F; double cost = hardness < 0 ? 1 : hardness * .05;
            if (cost <= 0) continue;
            if (remaining < cost) cost = (remaining + cost) / 2;
            cost = Math.min(remaining, cost); damage += (float) cost * 4; remaining -= Math.max(cost, .05);
            if (remaining <= 0) break;
        }
        if (damage > 0) StandDamage.hurt(level, living, level.damageSources().flyIntoWall(), damage);
        IMPACTS.remove(living);
    }
    public static void clear(Entity target) { IMPACTS.remove(target); }
    public static void clear() { IMPACTS.clear(); }
    private VampireCollisionImpact() { }
}
