package com.github.standobyte.jojo.fabric.mixin.timestop;

import com.github.standobyte.jojo.fabric.TimeStopService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow
    private void tickPassenger(Entity vehicle, Entity passenger) {
        throw new AssertionError();
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void jojoCancelTickNonPassenger(Entity entity, CallbackInfo ci) {
        if (entity instanceof com.github.standobyte.jojo.fabric.KnifeEntity knife)
            knife.setTimeStopped(TimeStopService.isTimeStopped(entity.level(), entity.chunkPosition()));
        if (TimeStopService.isFrozen(entity)) {
            if(entity instanceof com.github.standobyte.jojo.fabric.stand.hierophant.HGBarrierEntity barrier)barrier.tickFrozen();
            if (entity instanceof LivingEntity living && !(living instanceof net.minecraft.server.level.ServerPlayer) && living.damageCooldownTime > 0) living.damageCooldownTime--;
            for (Entity passenger : entity.getPassengers()) {
                tickPassenger(entity, passenger);
            }
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void jojoCancelTickPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (passenger instanceof com.github.standobyte.jojo.fabric.KnifeEntity knife)
            knife.setTimeStopped(TimeStopService.isTimeStopped(passenger.level(), passenger.chunkPosition()));
        if (TimeStopService.isFrozen(passenger)) {
            if(passenger instanceof com.github.standobyte.jojo.fabric.stand.hierophant.HGBarrierEntity barrier)barrier.tickFrozen();
            if (passenger instanceof LivingEntity living && !(living instanceof net.minecraft.server.level.ServerPlayer) && living.damageCooldownTime > 0) living.damageCooldownTime--;
            ci.cancel();
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void jojoCancelTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (TimeStopService.isTimeStopped(chunk.getLevel(), chunk.getPos())) {
            ci.cancel();
        }
    }

    @Inject(method = "isPositionTickingWithEntitiesLoaded", at = @At("HEAD"), cancellable = true)
    private void jojoPreserveScheduledTicks(long chunkPosPacked, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkPos pos = ChunkPos.unpack(chunkPosPacked);
        if (TimeStopService.isTimeStopped(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldTickBlocksAt(J)Z", at = @At("HEAD"), cancellable = true)
    private void jojoPauseBlockEntitiesInFrozenChunks(long chunkPosPacked, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        ChunkPos pos = ChunkPos.unpack(chunkPosPacked);
        if (TimeStopService.isTimeStopped(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
