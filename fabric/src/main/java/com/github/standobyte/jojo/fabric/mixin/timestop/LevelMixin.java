package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "shouldTickBlocksAt(J)Z", at = @At("HEAD"), cancellable = true)
    private void jojoCheckTimeStopBlockTicking(long chunkPosPacked, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        ChunkPos pos = ChunkPos.unpack(chunkPosPacked);
        if (level instanceof net.minecraft.server.level.ServerLevel && TimeStopService.isTimeStopped(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
