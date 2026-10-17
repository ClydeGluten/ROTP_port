package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedFireBlock;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
abstract class RestorationFireMixin {
    @Inject(method="checkBurnOut",at={
            @At(value="INVOKE",target="Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            @At(value="INVOKE",target="Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")})
    private void jojo$rememberBurntBlock(Level level,BlockPos pos,int chance,RandomSource random,int age,CallbackInfo ci){
        if(level instanceof ServerLevel server)RestorationHooks.remember(server,pos,level.getBlockState(pos),level.getBlockEntity(pos),java.util.List.of());
    }
    @Inject(method="getStateWithAge",at=@At("HEAD"),cancellable=true)
    private void jojo$preserveStandFire(LevelReader level,BlockPos pos,int age,CallbackInfoReturnable<BlockState> cir){
        if((Object)this instanceof MagiciansRedFireBlock fire)cir.setReturnValue(fire.stateWithAge(level,pos,age));
    }
}
