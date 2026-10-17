package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.client.HamonClackersRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemModelResolver.class)
abstract class HamonItemOwnerMixin{
    @Inject(method="appendItemLayers",at=@At("HEAD"))
    private void jojo$capture(ItemStackRenderState state,ItemStack stack,ItemDisplayContext display,Level level,ItemOwner owner,int seed,CallbackInfo ci){HamonClackersRenderer.begin(owner,display);com.github.standobyte.jojo.fabric.client.equipment.TommyGunRenderer.begin(owner,display);}
    @Inject(method="appendItemLayers",at=@At("RETURN"))
    private void jojo$clear(ItemStackRenderState state,ItemStack stack,ItemDisplayContext display,Level level,ItemOwner owner,int seed,CallbackInfo ci){HamonClackersRenderer.end();com.github.standobyte.jojo.fabric.client.equipment.TommyGunRenderer.end();}
}
