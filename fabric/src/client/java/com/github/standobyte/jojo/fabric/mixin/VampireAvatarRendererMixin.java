package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.client.*;
import com.github.standobyte.jojo.fabric.client.StoneMaskLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adapts the original custom armor model to current player render layers. */
@Mixin(AvatarRenderer.class)
public abstract class VampireAvatarRendererMixin extends LivingEntityRenderer<Avatar, AvatarRenderState, PlayerModel> {
    protected VampireAvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void jojoAddMask(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new StoneMaskLayer(this));
        addLayer(new VampireScarfLayer(this));
        addLayer(new VampireEyesLayer(this));
        addLayer(new ZombieLayer(this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void jojoExtractMask(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        var data = (VampireRenderData) state;
        data.jojo$vampireVisual(entity.getAttachedOrCreate(VampirePresentation.VISUAL));
        data.jojo$vampirePartialTick(TimeStopClient.isFrozen(entity) ? 0 : partialTick);
        ZombieLayer.extract(entity, state, partialTick);
        var head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof StoneMaskItem || head.is(VampireHamon.SCARF)) {
            state.headEquipment = head.copy();
            // The custom layer replaces vanilla's flat item-on-head presentation.
            state.headItem.clear();
        }
    }
}
