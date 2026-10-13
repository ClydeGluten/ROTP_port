package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StarPlatinumEntity;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumModel;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class StarPlatinumRenderer extends MobRenderer<StarPlatinumEntity, StarPlatinumRenderState, StarPlatinumModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(RipplesFabric.id("star_platinum"), "main");
    private static final Identifier TEXTURE = RipplesFabric.id("textures/entity/stand/star_platinum.png");

    public StarPlatinumRenderer(EntityRendererProvider.Context context) {
        super(context, new StarPlatinumModel(context.bakeLayer(LAYER)), 0);
        addLayer(new StarFingerLayer(this));
    }

    @Override public StarPlatinumRenderState createRenderState() { return new StarPlatinumRenderState(); }
    @Override public Identifier getTextureLocation(StarPlatinumRenderState state) { return TEXTURE; }

    @Override protected int getModelTint(StarPlatinumRenderState state) { return state.cameraTint(); }

    @Override protected RenderType getRenderType(StarPlatinumRenderState state, boolean visible, boolean translucent, boolean glowing) {
        if (state.cameraOpacity == 0) return null;
        if (state.cameraOpacity < 1 && (visible || translucent)) return RenderTypes.entityTranslucentCull(TEXTURE);
        return super.getRenderType(state, visible, translucent, glowing);
    }

    @Override protected boolean shouldRenderLayers(StarPlatinumRenderState state) {
        return state.cameraOpacity > 0 && super.shouldRenderLayers(state);
    }

    @Override public void extractRenderState(StarPlatinumEntity entity, StarPlatinumRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.cameraOpacity = StandCamera.opacity(entity);
        state.actionPhase = entity.actionPhase();
        state.heavyAttack = entity.heavyAttack();
        state.abilityKind = entity.abilityKind();
        state.actionTicks = Math.max(0, entity.actionDuration() - entity.actionTicks() + partialTick);
        state.actionDuration = entity.actionDuration();
    }
}
