package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ClientBlockEntityMixin {
    @Inject(method = "shouldTickBlocksAt(J)Z", at = @At("HEAD"), cancellable = true)
    private void jojo$freezeBlockEntityAnimation(long packed, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ClientLevel) {
            var chunk = ChunkPos.unpack(packed);
            if (TimeStopClient.isTimeStopped(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()))) cir.setReturnValue(false);
        }
    }
}
