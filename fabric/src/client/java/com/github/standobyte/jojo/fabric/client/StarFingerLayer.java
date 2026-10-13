package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumModel;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Original SPStarFingerModel dimensions, repeating shaft and texture, attached to the posed hand. */
public final class StarFingerLayer extends RenderLayer<StarPlatinumRenderState, StarPlatinumModel> {
    private static final Identifier TEXTURE = RipplesFabric.id("textures/entity/projectiles/sp_star_finger.png");
    private final ModelPart segment;
    private final ModelPart tip;
    public StarFingerLayer(StarPlatinumRenderer parent) {
        super(parent);
        var mesh = new MeshDefinition();
        // The old extending renderer used separate 8-pixel repeats and a 2-pixel end cap.
        mesh.getRoot().addOrReplaceChild("segment", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, 0, 2, 1, 8), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, 0, 2, 1, 2), PartPose.ZERO);
        var root = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        segment = root.getChild("segment"); tip = root.getChild("tip");
    }

    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, StarPlatinumRenderState state, float yaw, float pitch) {
        if (state.actionPhase != 7 || state.isInvisible || state.cameraOpacity == 0) return;
        float reach = state.getStarFingerReach();
        if (reach <= 0) return;
        getParentModel().setupAnim(state);
        pose.pushPose();
        PoseStack handPose = new PoseStack();
        ModelPart part = getParentModel().root();
        part.translateAndRotate(handPose);
        for (String child : new String[]{"body", "upperPart", "rightArmXRot", "rightArm", "rightForeArm"}) {
            part = part.getChild(child); part.translateAndRotate(handPose);
        }
        // Anchor at the posed hand, then align with the same aim direction as the server ray.
        var hand = handPose.last().pose().transformPosition(new org.joml.Vector3f(0, 6F / 16F, 0));
        pose.translate(hand.x, hand.y, hand.z);
        pose.rotateDegrees(Axis.XP, state.xRot);
        pose.rotateDegrees(Axis.YP, 180);
        var type = state.cameraOpacity < 1 ? RenderTypes.entityTranslucentCull(TEXTURE) : RenderTypes.entityCutout(TEXTURE);
        float shaft = Math.max(0, reach - 2F / 16F);
        while (shaft > 0.0001F) {
            float length = Math.min(0.5F, shaft);
            pose.pushPose();
            pose.scale(1, 1, length / 0.5F);
            collector.submitModelPart(segment, pose, type, light, OverlayTexture.NO_OVERLAY, null, state.cameraTint(), state.outlineColor);
            pose.popPose(); pose.translate(0, 0, length); shaft -= length;
        }
        pose.pushPose();
        pose.scale(1, 1, Math.min(1, reach / (2F / 16F)));
        collector.submitModelPart(tip, pose, type, light, OverlayTexture.NO_OVERLAY, null, state.cameraTint(), state.outlineColor);
        pose.popPose(); pose.popPose();
    }
}
