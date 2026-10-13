package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stand Virus was uncurable by consumables upstream. Preserve other milk cures. */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public abstract class StandVirusMilkMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void jojo$keepVirus(Level level, ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Boolean> result) {
        if (!entity.hasEffect(StandItems.STAND_VIRUS)) return;
        boolean removed = false;
        for (var effect : java.util.List.copyOf(entity.getActiveEffects())) {
            if (!effect.is(StandItems.STAND_VIRUS)) removed |= entity.removeEffect(effect.getEffect());
        }
        result.setReturnValue(removed);
    }
}
