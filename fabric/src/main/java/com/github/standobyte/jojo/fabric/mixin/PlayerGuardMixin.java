package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply guard after vanilla armor/enchantments, before absorption, only for an accepted incoming hit. */
@Mixin(Player.class)
abstract class PlayerGuardMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private void jojo$earnResolve(ServerLevel level, DamageSource source, float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.github.standobyte.jojo.fabric.ProgressionService.onAttack((Player) (Object) this, source, amount);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void jojo$resolveResistance(Player self, float proposedHealth,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
            ServerLevel level, DamageSource source, float amount) {
        float health = self instanceof ServerPlayer player
                ? com.github.standobyte.jojo.fabric.ProgressionService.incomingHealth(player, source, proposedHealth) : proposedHealth;
        original.call(self, health);
    }

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float jojo$guardDamage(float damage, ServerLevel level, DamageSource source, float originalDamage) {
        return (Object) this instanceof ServerPlayer player ? StandService.guardDamage(player, source, damage) : damage;
    }
}
