package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.HamonCharges;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
abstract class HamonEntityMixin {
    @Inject(method="baseTick",at=@At("TAIL")) private void jojo$tickCharge(CallbackInfo ci){HamonCharges.tick((Entity)(Object)this);}
}
