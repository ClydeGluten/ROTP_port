package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandDefinitions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Shared lifecycle/control boundary; source-specific entities retain their own action implementations. */
public interface StandManifestation {
    Mob entity();
    StandDefinitions.Definition definition();
    void setOwner(ServerPlayer player);
    ServerPlayer owner();
    int ownerId();
    int actionPhase();
    int actionTicks();
    int actionDuration();
    int abilityKind();
    boolean heavyAttack();
    float finisherCharge();
    int uiRecoveryTicks();
    boolean allowsStaminaRegen();
    float userWalkSpeed();
    boolean manuallyControlled();
    Vec3 bodyAnchor();
    void translateControlAnchor(Vec3 movement);
    boolean beginAttack(boolean heavy);
    boolean beginHeld(boolean guard);
    boolean beginSpecial(int phase);
    boolean beginUppercut();
    boolean startControl();
    void stopControl();
    void control(float sideways, float forward, float vertical, float yaw, float pitch);
    void stopHeld();
    void refreshHeld();
    float protectOwner(DamageSource source, float damage);
    void playOriginal(String sound);
}
