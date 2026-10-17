package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampireLifecycle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class VampireCoffinTimeMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/clock/ServerClockManager$MoveResult;"))
    private ServerClockManager.MoveResult jojo$coffinNight(ServerClockManager clocks, Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> marker, Operation<ServerClockManager.MoveResult> original) {
        var changed = VampireLifecycle.sleepToNight((ServerLevel) (Object) this, clocks, clock);
        return changed == null ? original.call(clocks, clock, marker) : changed;
    }
}
