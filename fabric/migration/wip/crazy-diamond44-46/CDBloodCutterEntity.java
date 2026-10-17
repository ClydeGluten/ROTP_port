package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Source blood cutter remains physical and feeds vampires instead of damaging them. */
public final class CDBloodCutterEntity extends CDProjectile {
    public CDBloodCutterEntity(EntityType<? extends CDBloodCutterEntity> type, Level level) { super(type, level); }
    public CDBloodCutterEntity(CrazyDiamondEntity stand) {
        this(CrazyDiamondCombatActions.BLOOD_CUTTER_ENTITY, stand.level()); setOwner(stand.owner()); setShootingPos(stand.owner());
        damageFactor = (float) stand.attackDamage() / 8; speedFactor = stand.attackSpeed() / 8;
    }
    @Override protected int lifespan() { return 100; }
    @Override protected float baseDamage() { return 4; }
    @Override protected float breakHardness() { return 0; }
    @Override protected boolean hurtTarget(ServerLevel level, Entity target) {
        if (target instanceof LivingEntity living) {
            var vampire = living.getAttachedOrCreate(VampireAttachments.PROGRESS);
            if (vampire.vampire()) {
                living.setAttached(VampireAttachments.PROGRESS, vampire.withBlood(vampire.blood() + 5));
                target.playSound(JojoSounds.get("vampire_blood_drain"), 1, 1); discard(); return false;
            }
        }
        return super.hurtTarget(level, target);
    }
    @Override protected void breakProjectile(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && !(entityHit.getEntity() instanceof LivingEntity)) return;
        super.breakProjectile(hit);
        if (!isInWaterOrBubble() && level() instanceof ServerLevel server) {
            CDRecoveryModifiers.splashBlood(server, getBoundingBox().getCenter(), 4, 6.4F,
                    getOwner() instanceof LivingEntity owner ? owner : null);
            server.playSound(null, getX(), getY(), getZ(), JojoSounds.get("water_splash"), SoundSource.NEUTRAL, 1, 1);
        }
    }
    public static boolean canHaveBloodDropsOn(Entity target, LivingEntity owner) {
        var stand = owner instanceof net.minecraft.server.level.ServerPlayer player ? StandService.currentManifestation(player) : null;
        return target != owner && (stand == null || target != stand.entity()) && !(target instanceof StandManifestation) && !target.isInWaterOrBubble();
    }
}
