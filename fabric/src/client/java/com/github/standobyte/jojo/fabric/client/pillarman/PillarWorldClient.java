package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.pillarman.PillarWorldContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.particle.LavaParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;

/** Original fluid sprites/pop particles and nine locally-lit engraving quads. */
public final class PillarWorldClient {
    public static void initialize() {
        FluidRenderingRegistry.register(PillarWorldContent.BOILING_BLOOD, PillarWorldContent.FLOWING_BOILING_BLOOD,
                new FluidModel.Unbaked(new Material(RipplesFabric.id("block/boiling_blood_still")),
                        new Material(RipplesFabric.id("block/boiling_blood_flow")), null, state -> -1));
        ParticleProviderRegistry.getInstance().register(PillarWorldContent.BOILING_BLOOD_POP, LavaParticle.Provider::new);
        EntityRenderers.register(PillarWorldContent.ENGRAVING, EngravingRenderer::new);
    }
    public static final class EngravingState extends EntityRenderState {
        int texture;
        float rotation;
        final int[] light = new int[9];
    }
    public static final class EngravingRenderer extends EntityRenderer<PillarWorldContent.EngravingEntity, EngravingState> {
        public EngravingRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public EngravingState createRenderState() { return new EngravingState(); }
        @Override public void extractRenderState(PillarWorldContent.EngravingEntity entity, EngravingState state, float partial) {
            super.extractRenderState(entity, state, partial);
            state.texture = entity.getTextureId(); state.rotation = entity.getYRot();
            Direction direction = entity.getDirection();
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                double ex = entity.getX(), ez = entity.getZ(), offset = x - 1;
                if (direction == Direction.NORTH) ex += offset;
                if (direction == Direction.SOUTH) ex -= offset;
                if (direction == Direction.WEST) ez -= offset;
                if (direction == Direction.EAST) ez += offset;
                state.light[x * 3 + y] = LightCoordsUtil.getLightCoords(entity.level(), BlockPos.containing(ex, entity.getY() + y - 1, ez));
            }
        }
        @Override public void submit(EngravingState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            pose.pushPose(); pose.rotateDegrees(Axis.YP, 180 - state.rotation); pose.scale(1F / 16, 1F / 16, 1F / 16);
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(RipplesFabric.id("textures/engraving/engraving_" + (state.texture + 1) + ".png")), (entry, vertices) -> {
                for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                    float left = -24 + x * 16, right = left + 16, bottom = -24 + y * 16, top = bottom + 16;
                    float u0 = (3 - x) / 3F, u1 = (2 - x) / 3F, v0 = (3 - y) / 3F, v1 = (2 - y) / 3F;
                    int light = state.light[x * 3 + y];
                    vertex(entry, vertices, right, bottom, u1, v0, light); vertex(entry, vertices, left, bottom, u0, v0, light);
                    vertex(entry, vertices, left, top, u0, v1, light); vertex(entry, vertices, right, top, u1, v1, light);
                }
            });
            pose.popPose(); super.submit(state, pose, collector, camera);
        }
        private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float u, float v, int light) {
            vertices.addVertex(pose, x, y, -.5F).setColor(191, 191, 191, 191).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        }
    }
    private PillarWorldClient() { }
}
