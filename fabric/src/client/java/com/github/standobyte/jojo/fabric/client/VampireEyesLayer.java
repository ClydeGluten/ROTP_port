package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Original VampireEyesLayer: full-bright original texture at >=80% blood, respecting the owner's preference. */
public final class VampireEyesLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    public VampireEyesLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        if(!state.isInvisible && ((VampireRenderData)state).jojo$vampireVisual().glowing())
            collector.submitModel(getParentModel(),state,pose,RenderTypes.entityTranslucent(RipplesFabric.id("textures/entity/biped/vampire_eyes.png")),
                    0xF000F0,OverlayTexture.NO_OVERLAY,-1,null,state.outlineColor);
    }
}
