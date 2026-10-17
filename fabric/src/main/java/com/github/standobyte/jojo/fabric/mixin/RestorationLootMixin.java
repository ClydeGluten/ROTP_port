package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original LootTableMixin: capture the generated result, even when queried before a mutation. */
@Mixin(LootTable.class)
abstract class RestorationLootMixin {
    @Inject(method="getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",at=@At("RETURN"))
    private void jojo$rememberLoot(LootContext context,CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir){
        if(!LootContextParamSets.BLOCK.required().stream().allMatch(context::hasParameter))return;
        var state=context.getOptional(LootContextParams.BLOCK_STATE);
        var origin=context.getOptional(LootContextParams.ORIGIN);
        if(state!=null&&origin!=null)RestorationHooks.captureDrops(context.getLevel(),BlockPos.containing(origin),state,
                context.getOptional(LootContextParams.BLOCK_ENTITY),cir.getReturnValue());
    }
}
