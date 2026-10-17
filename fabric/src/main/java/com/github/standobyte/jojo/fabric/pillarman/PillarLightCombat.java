package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Set;
import java.util.UUID;

/** Source damage, selection and defense; callers cannot supply arbitrary target IDs or hit amounts. */
public final class PillarLightCombat {
    static void flash(ServerPlayer player) {
        Vec3 center = player.getBoundingBox().getCenter();
        // MCUtil.entitiesAround is a centered cube, not a spherical distance cutoff.
        for (var target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(16),
                entity -> entity != player && entity.hasLineOfSight(player)
                        && !(entity instanceof StandManifestation stand && stand.ownerId() == player.getId()))) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, player.distanceTo(target) < 5 ? 200 : 80, 0, true, true, false));
            if (!(target instanceof Player) && !(target instanceof StandManifestation))
                target.addEffect(new MobEffectInstance(VampireCombatService.STUN, 60, 0, true, true, false));
        }
    }
    static void flashParticles(ServerPlayer player, boolean decoy) {
        emit(player, 0, decoy);
    }
    private static void emit(Entity entity, int count, boolean decoy) {
        var packet = new PillarLightActions.Emitter(entity.getId(), count, decoy);
        var recipients = new java.util.HashSet<>(net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(entity));
        if (entity instanceof ServerPlayer player) recipients.add(player);
        for (var player : recipients) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
    }
    static void sparks(Entity entity, int count) {
        emit(entity, count, false);
    }
    static void slash(ServerPlayer player) {
        LivingEntity target = target(player);
        if (target != null && bladeHit(player, target)) {
            sparks(target, 9); PillarmanService.sound(player, "the_world_punch_heavy_entity", 1.2F, .8F);
            target.knockback(.75, player.getX() - target.getX(), player.getZ() - target.getZ(), player.damageSources().playerAttack(player), 0);
            target.syncVelocity = true; VampireCombatService.onClawHit(target, player);
        }
    }
    public static AABB slashHitbox(LivingEntity user) {
        double yaw = -Math.toRadians(user.getYRot());
        Vec3 center = user.position().add(Math.sin(yaw) * user.getBbWidth() * .75, 0, Math.cos(yaw) * user.getBbWidth() * .75);
        return new AABB(center, center).inflate(user.getBbWidth() * 1.5, .125, user.getBbWidth() * 1.5).expandTowards(0, user.getBbHeight() / 2, 0);
    }
    static void dash(ServerPlayer player, Set<UUID> struck) {
        for (var target : player.level().getEntitiesOfClass(LivingEntity.class, slashHitbox(player), entity -> entity != player && player.canAttack(entity))) {
            if (!struck.add(target.getUUID()) || !bladeHit(player, target)) continue;
            Vec3 delta = target.position().subtract(player.position());
            float targetYaw = (float) Math.toDegrees(-Math.atan2(delta.x, delta.z));
            boolean left = Mth.wrapDegrees(player.yBodyRot - targetYaw) < 0;
            float knockbackYaw = targetYaw + (60 + player.getRandom().nextFloat() * 30) * (left ? 1 : -1);
            target.knockback(.75, Math.sin(Math.toRadians(knockbackYaw)), -Math.cos(Math.toRadians(knockbackYaw)), player.damageSources().playerAttack(player), 0);
            target.syncVelocity = true; sparks(target, 60);
        }
    }
    private static boolean bladeHit(ServerPlayer player, LivingEntity target) {
        DamageSource source = player.damageSources().playerAttack(player);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + 5;
        // Original 15F is clamped to [0,1]: fully compensate armor while preserving ordinary resistance/enchantments.
        if (target.getArmorValue() > 0) {
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), low = damage, high = damage * 5;
            for (int i = 0; i < 24; i++) {
                float middle = (low + high) * .5F;
                if (CombatRules.getDamageAfterAbsorb(target, middle, source, target.getArmorValue(), toughness) < damage) low = middle;
                else high = middle;
            }
            damage = high;
        }
        return target.hurtServer(player.level(), source, damage);
    }
    static void barrage(ServerPlayer player) {
        LivingEntity target = target(player);
        if (target != null) {
            // Free main hand is required throughout the hold, so the attribute has no held-weapon damage.
            if (StandDamage.hurt(player.level(), target, player.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .2F)) sparks(target, 12);
        } else {
            var hit = blockTarget(player);
            if (hit.getType() == HitResult.Type.BLOCK) {
                var pos = hit.getBlockPos(); var state = player.level().getBlockState(pos);
                if (!state.isAir() && player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS) && player.mayBuild() && player.level().mayInteract(player, pos)
                        && !player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer())) {
                    float hardness = state.getDestroySpeed(player.level(), pos);
                    double duration = player.isCreative() ? 0 : hardness / player.getDestroySpeed(state);
                    if (!player.isCreative() && !player.hasCorrectToolForDrops(state)) duration *= 10D / 3;
                    if (hardness >= 0 && duration <= 2.5 * Math.sqrt(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                            && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, state, player.level().getBlockEntity(pos)))
                        player.level().destroyBlock(pos, !player.isCreative(), player);
                    else {
                        var sound = state.getSoundType();
                        player.level().playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 8, sound.getPitch() * .5F);
                    }
                }
            }
        }
        PillarmanService.sound(player, "silver_chariot_barrage_swipe", .5F, 1);
    }
    private static BlockHitResult blockTarget(ServerPlayer player) {
        return player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }
    private static LivingEntity target(ServerPlayer player) {
        double range = player.entityInteractionRange(); Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double closest = block.getType() == HitResult.Type.MISS ? range * range : start.distanceToSqr(block.getLocation()); LivingEntity result = null;
        for (var entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                target -> target != player && target.isAlive() && !target.isSpectator())) {
            var bounds = entity.getBoundingBox().inflate(entity.getPickRadius()); var hit = bounds.contains(start) ? java.util.Optional.of(start) : bounds.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= closest) { closest = start.distanceToSqr(hit.get()); result = entity; }
        }
        return result;
    }
    /** Root invokes before any damage processing, including damage which bypasses ordinary invulnerability. */
    public static boolean cancelIncomingDamage(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !PillarLightActions.active(player)
                || !PillarmanBaseActions.canContinue(player, 1, false, false)
                || PillarmanService.state(player).mode() != com.github.standobyte.jojo.core.PillarmanProgress.Mode.LIGHT) return false;
        var phase = PillarLightActions.state(player).phase();
        if (phase == PillarLightActions.Phase.DASH) return true;
        if (phase != PillarLightActions.Phase.BARRAGE) return false;
        var attacker = source.getDirectEntity();
        if (attacker instanceof PillarmanBaseActions.EvasionProjectile custom) return custom.canBeEvaded(player) && !custom.standDamage();
        if (!(attacker instanceof Projectile)) return false;
        boolean cancel = false;
        double cone = Math.cos(Math.toRadians(30 + 10D * 30 / 16));
        // Preserve original query semantics: any eligible approaching projectile in reach cancels this attack event.
        for (var projectile : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(player.blockInteractionRange()), e -> e.isAlive() && !e.isPickable())) {
            if (player.getLookAngle().dot(projectile.getDeltaMovement().reverse().normalize()) < cone) continue;
            cancel = true;
            if (!projectile.onGround()) {
                sparks(projectile, 12); player.level().playSound(null, projectile.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .4F, 1.35F);
            }
        }
        return cancel;
    }
    private PillarLightCombat() { }
}
