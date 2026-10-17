package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original ResolveEffect's attribute-install callback has no entity argument on modern MobEffect. */
@Mixin(LivingEntity.class)
abstract class ResolveEffectLifecycleMixin {
    @Inject(method="onEffectAdded",at=@At("TAIL"))
    private void jojo$resolveAdded(MobEffectInstance effect,Entity source,CallbackInfo ci){
        if((Object)this instanceof ServerPlayer player&&effect.is(StandItems.RESOLVE))
            ProgressionService.resolveEffectStarted(player,effect.getAmplifier(),effect.getDuration());
    }
    @Inject(method="onEffectUpdated",at=@At("TAIL"))
    private void jojo$resolveUpdated(MobEffectInstance effect,boolean reapply,Entity source,CallbackInfo ci){
        if(reapply&&(Object)this instanceof ServerPlayer player&&effect.is(StandItems.RESOLVE))
            ProgressionService.resolveEffectStarted(player,effect.getAmplifier(),effect.getDuration());
    }
}
