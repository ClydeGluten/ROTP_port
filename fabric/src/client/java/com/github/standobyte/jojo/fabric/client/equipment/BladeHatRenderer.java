package com.github.standobyte.jojo.fabric.client.equipment;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.equipment.BladeHatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Original BladeHatEntityModel cubes and SimpleEntityRenderer transforms. */
public final class BladeHatRenderer extends EntityRenderer<BladeHatEntity, BladeHatRenderer.State> {
    public static final class State extends EntityRenderState { float rotation; }
    private final ModelPart hat = model(false);
    public BladeHatRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BladeHatEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.rotation = Mth.lerp(partial, entity.yRotO, entity.getYRot());
        if (!entity.isInGround() && !TimeStopClient.isFrozen(entity)) state.rotation += state.ageInTicks * 36;
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.scale(1, -1, -1); hat.yRot = state.rotation * Mth.DEG_TO_RAD;
        collector.submitModelPart(hat, pose, RenderTypes.entityCutout(RipplesFabric.id("textures/entity/projectiles/opened_blade_hat.png")), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }
    public static ModelPart model(boolean worn) {
        var mesh = new MeshDefinition();
        var hat = mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(0,23).addBox(-4,-6,-4,8,6,8)
                .texOffs(32,23).addBox(-4,-6.1F,-4,8,4,8,new CubeDeformation(.25F))
                .texOffs(0,12).addBox(-4.5F,-2.85F,-4.5F,9,2,9,new CubeDeformation(-.125F))
                .texOffs(36,12).addBox(-3,-2.85F,-4.6F,6,2,1,new CubeDeformation(-.1F))
                .texOffs(0,37).addBox(-1.5F,-2.85F,-4.75F,3,2,1,new CubeDeformation(.1F))
                .texOffs(0,0).addBox(-4,0,-6,8,0,12), PartPose.offset(0,worn ? -4 : 0,0));
        hat.addOrReplaceChild("cube_r1",CubeListBuilder.create().texOffs(32,35).mirror().addBox(-5,0,-5,2,0,10),PartPose.offsetAndRotation(8.6198F,-1.9135F,0,0,0,-.3927F));
        hat.addOrReplaceChild("cube_r2",CubeListBuilder.create().texOffs(32,35).addBox(3,0,-5,2,0,10),PartPose.offsetAndRotation(-8.6198F,-1.9135F,0,0,0,.3927F));
        hat.addOrReplaceChild("cube_r3",CubeListBuilder.create().texOffs(8,37).mirror().addBox(0,-2.5F,-1,0,4,2),PartPose.offsetAndRotation(-4.5746F,-3.5345F,.9056F,-.3927F,0,-.2182F));
        hat.addOrReplaceChild("cube_r4",CubeListBuilder.create().texOffs(36,15).mirror().addBox(0,-2,-1.5F,0,4,3),PartPose.offsetAndRotation(-4.5F,-3.6F,0,-.1745F,0,-.2182F));
        hat.addOrReplaceChild("cube_r5",CubeListBuilder.create().texOffs(8,37).addBox(0,-2.5F,-1,0,4,2),PartPose.offsetAndRotation(4.5746F,-3.5345F,.9056F,-.3927F,0,.2182F));
        hat.addOrReplaceChild("cube_r6",CubeListBuilder.create().texOffs(36,15).addBox(0,-2,-1.5F,0,4,3),PartPose.offsetAndRotation(4.5F,-3.6F,0,-.1745F,0,.2182F));
        return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("hat");
    }
}
