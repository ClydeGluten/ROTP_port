package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @org.spongepowered.asm.mixin.Shadow private float lastSentHealth;
    @org.spongepowered.asm.mixin.Shadow private int lastSentFood;
    @org.spongepowered.asm.mixin.Shadow private boolean lastFoodSaturationZero;
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void jojoCancelFrozenPlayerTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (TimeStopService.isFrozen(player)) {
            if (player.damageCooldownTime > 0) player.damageCooldownTime--;
            // The ordinary doTick health sync is paused too. Damage is immediate in stopped time,
            // so the victim must receive the same authoritative health without advancing simulation.
            var food = player.getFoodData();
            boolean emptySaturation = food.getSaturationLevel() == 0;
            if (lastSentHealth != player.getHealth() || lastSentFood != food.getFoodLevel() || lastFoodSaturationZero != emptySaturation) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                        player.getHealth(), food.getFoodLevel(), food.getSaturationLevel()));
                lastSentHealth = player.getHealth();
                lastSentFood = food.getFoodLevel();
                lastFoodSaturationZero = emptySaturation;
            }
            ci.cancel();
        }
    }
}
