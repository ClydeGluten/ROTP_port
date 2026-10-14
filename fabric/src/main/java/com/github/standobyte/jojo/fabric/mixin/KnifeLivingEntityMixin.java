package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StuckKnives;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frozen entities skip LivingEntity.tick, so embedded knives retain their original remaining time. */
@Mixin(LivingEntity.class)
public abstract class KnifeLivingEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void jojoTickStuckKnives(CallbackInfo ci) {
        StuckKnives.tick((LivingEntity) (Object) this);
    }
}
