package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.StuckKnives;
import com.github.standobyte.jojo.fabric.client.StuckKnifeLayer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientSetup.addLayers originally attached KnifeLayer to both player skin renderers only. */
@Mixin(AvatarRenderer.class)
public abstract class KnifeAvatarRendererMixin extends LivingEntityRenderer<Avatar, AvatarRenderState, PlayerModel> {
    protected KnifeAvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void jojoAddStuckKnifeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new StuckKnifeLayer(this, slim));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void jojoExtractStuckKnives(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((FabricRenderState) state).setData(StuckKnifeLayer.COUNT, entity instanceof Player ? StuckKnives.count(entity) : 0);
    }
}
