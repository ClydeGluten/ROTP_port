package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StandItems;
import com.github.standobyte.jojo.fabric.VampireItems;
import com.github.standobyte.jojo.fabric.VampireCombatService;
import com.github.standobyte.jojo.fabric.VampireHamon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original uncurable statuses survive consumables. Bleeding and Freeze retain their normal milk cures. */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public abstract class StandVirusMilkMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void jojo$keepVirus(Level level, ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Boolean> result) {
        if (entity.getActiveEffects().stream().noneMatch(StandVirusMilkMixin::uncurable)) return;
        boolean removed = false;
        for (var effect : java.util.List.copyOf(entity.getActiveEffects())) {
            if (!uncurable(effect)) removed |= entity.removeEffect(effect.getEffect());
        }
        result.setReturnValue(removed);
    }
    private static boolean uncurable(MobEffectInstance effect) {
        return com.github.standobyte.jojo.fabric.StandPerception.uncurable(effect.getEffect()) || com.github.standobyte.jojo.fabric.pillarman.PillarLightActions.uncurable(effect.getEffect()) || com.github.standobyte.jojo.fabric.pillarman.PillarWindActions.uncurable(effect.getEffect()) || effect.is(StandItems.STAND_VIRUS) || effect.is(VampireItems.SUN_BURN)
                || effect.is(VampireCombatService.STUN) || effect.is(VampireHamon.SPREAD)
                || effect.is(com.github.standobyte.jojo.fabric.HamonEffects.SHOCK)
                || effect.is(com.github.standobyte.jojo.fabric.HamonEffects.HYPNOSIS)
                || effect.is(com.github.standobyte.jojo.fabric.HamonEffects.IMMOBILIZE)
                || effect.is(com.github.standobyte.jojo.fabric.HamonInheritance.CHEAT_DEATH);
    }
}
