package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.SpaceRipperStingyEyesEntity;
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
import net.minecraft.world.phys.Vec3;

/** Original four translucent beam sides, full-bright texture, width and repeated UVs. */
public final class SpaceRipperStingyEyesRenderer extends EntityRenderer<SpaceRipperStingyEyesEntity, SpaceRipperStingyEyesRenderer.State> {
    private static final Identifier TEXTURE = RipplesFabric.id("textures/entity/projectiles/space_ripper_stingy_eyes.png");
    public static final class State extends EntityRenderState { Vec3 beam = Vec3.ZERO; }
    public SpaceRipperStingyEyesRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(SpaceRipperStingyEyesEntity entity, float partialTick) { return entity.beamBounds(); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(SpaceRipperStingyEyesEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick); state.beam = entity.origin(partialTick).subtract(entity.getPosition(partialTick));
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.beam.lengthSqr() < 1E-8) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-state.beam.x, state.beam.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(state.beam.y, Math.sqrt(state.beam.x * state.beam.x + state.beam.z * state.beam.z)));
        float length = (float) state.beam.length();
        pose.pushPose(); pose.rotateDegrees(Axis.YP, -90 - yaw); pose.rotateDegrees(Axis.ZP, -pitch); pose.scale(1, .15F, .15F);
        float cameraPitch = camera.xRot;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentCull(TEXTURE), (entry, vertices) -> {
            var copy = entry.copy();
            // Original beam lighting is relative to camera pitch, independent of beam direction.
            copy.normal().identity().rotateX((float) Math.toRadians(cameraPitch));
            for (int side = 0; side < 4; side++) {
                copy.rotateDegrees(Axis.XP, 90);
                float normalY = side == 0 ? -1 : side == 2 ? 1 : 0;
                float normalZ = side == 1 ? -1 : side == 3 ? 1 : 0;
                var face = copy.copy();
                face.translate(0, 0, .125F);
                quad(face, vertices, length, normalY, normalZ);
                face.rotateDegrees(Axis.XP, 180);
                quad(face, vertices, length, -normalY, -normalZ);
            }
        });
        pose.popPose();
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, float length, float normalY, float normalZ) {
        vertex(pose, vertices, 0, -1, 0, 0, normalY, normalZ);
        vertex(pose, vertices, length, -1, length, 0, normalY, normalZ);
        vertex(pose, vertices, length, 1, length, 1, normalY, normalZ);
        vertex(pose, vertices, 0, 1, 0, 1, normalY, normalZ);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float u, float v, float normalY, float normalZ) {
        vertices.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(pose, 0, normalY, normalZ);
    }
}
