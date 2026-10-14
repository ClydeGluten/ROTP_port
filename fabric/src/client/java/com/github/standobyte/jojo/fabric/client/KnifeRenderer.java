package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.KnifeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The original KnifeRenderer's six quads, UVs and transforms, submitted through the current render-state API. */
public final class KnifeRenderer extends EntityRenderer<KnifeEntity, KnifeRenderer.State> {
    public static final class State extends EntityRenderState {
        public float yaw;
        public float pitch;
        public Identifier texture;
    }

    public KnifeRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(KnifeEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.texture = entity.getKnifeTexture();
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        submitKnife(pose, collector, state.texture, state.yaw, state.pitch, state.lightCoords);
    }

    public static void submitKnife(PoseStack pose, SubmitNodeCollector collector, Identifier texture,
                                    float yaw, float pitch, int light) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, yaw - 90F);
        pose.translate(0, 0.125, 0);
        pose.rotateDegrees(Axis.ZP, pitch);
        pose.scale(0.0375F, 0.0375F, 0.0375F);
        pose.translate(1, 0, 0);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(texture),
                (entry, vertices) -> renderGeometry(entry, vertices, light));
        pose.popPose();
    }

    /** Local knife geometry; the submission owns its copied pose and never mutates the caller's matrix. */
    public static void renderGeometry(PoseStack.Pose submittedPose, VertexConsumer vertices, int light) {
        PoseStack.Pose pose = submittedPose.copy();
        vertex(pose, vertices, -4, -2, -2, 2F / 32, 27F / 32, -1, 0, 0, light);
        vertex(pose, vertices, -4, -2, 2, 5F / 32, 27F / 32, -1, 0, 0, light);
        vertex(pose, vertices, -4, 2, 2, 5F / 32, 30F / 32, -1, 0, 0, light);
        vertex(pose, vertices, -4, 2, -2, 2F / 32, 30F / 32, -1, 0, 0, light);

        vertex(pose, vertices, -4, 2, -2, 2F / 32, 27F / 32, 1, 0, 0, light);
        vertex(pose, vertices, -4, 2, 2, 5F / 32, 27F / 32, 1, 0, 0, light);
        vertex(pose, vertices, -4, -2, 2, 5F / 32, 30F / 32, 1, 0, 0, light);
        vertex(pose, vertices, -4, -2, -2, 2F / 32, 30F / 32, 1, 0, 0, light);

        pose.rotateDegrees(Axis.XP, 90);
        blade(pose, vertices, 12F / 32, 21F / 32, light);
        pose.rotateDegrees(Axis.XP, 90);
        blade(pose, vertices, 0, 9F / 32, light);
        pose.rotateDegrees(Axis.XP, 90);
        blade(pose, vertices, 21F / 32, 12F / 32, light);
        pose.rotateDegrees(Axis.XP, 90);
        blade(pose, vertices, 9F / 32, 0, light);
    }

    private static void blade(PoseStack.Pose pose, VertexConsumer vertices, float top, float bottom, int light) {
        vertex(pose, vertices, -8, -3, 0, 0, top, 0, 1, 0, light);
        vertex(pose, vertices, 8, -3, 0, 16F / 32, top, 0, 1, 0, light);
        vertex(pose, vertices, 8, 3, 0, 16F / 32, bottom, 0, 1, 0, light);
        vertex(pose, vertices, -8, 3, 0, 0, bottom, 0, 1, 0, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                               float u, float v, float normalX, float normalY, float normalZ, int light) {
        vertices.addVertex(pose, x, y, z).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ);
    }
}
