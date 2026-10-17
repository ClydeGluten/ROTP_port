package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.HamonCharges;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Creeper.class)
abstract class HamonCreeperMixin {
    @Shadow private int explosionRadius;
    @Inject(method="explodeCreeper",at=@At("HEAD"))private void jojo$hamonExplosion(CallbackInfo ci){var self=(Creeper)(Object)this;HamonCharges.explode(self,explosionRadius*(self.isPowered()?2:1));}
}
