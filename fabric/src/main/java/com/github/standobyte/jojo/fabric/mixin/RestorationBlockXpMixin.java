package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
abstract class RestorationBlockXpMixin {
    @Inject(method="popExperience",at=@At("HEAD"))
    private void jojo$recordPlayerBreakXp(ServerLevel level,BlockPos pos,int amount,CallbackInfo ci){
        RestorationHooks.capturePlayerBreakXp(level,pos,amount);
    }
}
