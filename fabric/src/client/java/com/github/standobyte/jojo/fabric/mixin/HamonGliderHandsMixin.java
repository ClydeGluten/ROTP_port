package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.LeavesGliderEntity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LocalPlayer.class)
abstract class HamonGliderHandsMixin {
    @Inject(method="isHandsBusy",at=@At("HEAD"),cancellable=true)
    private void jojo$gliderHands(CallbackInfoReturnable<Boolean> cir){if(((LocalPlayer)(Object)this).getVehicle() instanceof LeavesGliderEntity)cir.setReturnValue(true);}
}
