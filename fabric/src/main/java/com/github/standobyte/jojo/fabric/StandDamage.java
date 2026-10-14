package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.mixin.StandDamageAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Port of DamageUtil.hurtThroughInvulTicks: Stand action timing owns its hit cadence. */
public final class StandDamage {
    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        int cooldown = target.damageCooldownTime;
        var access = (StandDamageAccess) target;
        float lastHurt = access.jojo$getLastHurt();
        target.damageCooldownTime = 0;
        try {
            return target.hurtServer(level, source, amount);
        } finally {
            // Keep other attacks' immunity intact, including when a hit is blocked or rejected.
            target.damageCooldownTime = cooldown;
            access.jojo$setLastHurt(lastHurt);
        }
    }

    private StandDamage() {}
}
