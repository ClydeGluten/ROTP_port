package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.LeavesGliderEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Source glider cannot be targeted by its own passenger. */
@Mixin(LeavesGliderEntity.class)
abstract class HamonGliderClientMixin {
    @Inject(method="isPickable",at=@At("HEAD"),cancellable=true)
    private void jojo$gliderTarget(CallbackInfoReturnable<Boolean> cir){
        var glider=(LeavesGliderEntity)(Object)this;var player=Minecraft.getInstance().player;
        if(glider.level().isClientSide()&&player!=null&&player.getRootVehicle()==glider.getRootVehicle())cir.setReturnValue(false);
    }
}
