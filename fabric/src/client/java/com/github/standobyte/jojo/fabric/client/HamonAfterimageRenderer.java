package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.HamonAfterimageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Source AfterimageRenderer delegates the original entity renderer at its delayed position. */
public final class HamonAfterimageRenderer extends EntityRenderer<HamonAfterimageEntity,HamonAfterimageRenderer.State> {
    public static final class State extends EntityRenderState {
        EntityRenderer renderer;
        EntityRenderState origin;
    }
    public HamonAfterimageRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override @SuppressWarnings("unchecked") public void extractRenderState(HamonAfterimageEntity entity, State state, float partial) {
        super.extractRenderState(entity,state,partial);
        state.origin=null;
        var owner=entity.owner();
        if(owner==null || !entity.shouldRender() || owner==Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson())return;
        state.renderer=entityRenderDispatcher.getRenderer(owner);
        state.origin=state.renderer.createRenderState(owner,partial);
        state.origin.x=state.x;state.origin.y=state.y;state.origin.z=state.z;
    }
    @Override @SuppressWarnings("unchecked") public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(state.origin!=null)state.renderer.submit(state.origin,pose,collector,camera);
    }
}
