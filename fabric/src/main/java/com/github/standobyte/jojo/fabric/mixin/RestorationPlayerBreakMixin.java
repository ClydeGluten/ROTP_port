package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class RestorationPlayerBreakMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @WrapMethod(method="destroyBlock")
    private boolean jojo$playerXpScope(BlockPos pos,Operation<Boolean> original){
        return RestorationHooks.duringPlayerBreak(level,pos,()->original.call(pos));
    }
    @Inject(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private void jojo$creativeBreak(BlockPos pos,CallbackInfoReturnable<Boolean> cir){
        if(player.isCreative())RestorationHooks.remember(level,pos,level.getBlockState(pos),level.getBlockEntity(pos),java.util.List.of());
    }
}
