package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.pillarman.PillarBodyParts;
import com.github.standobyte.jojo.fabric.pillarman.PillarRibsEntity;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Literal PillarmanHornModel/RibModel cubes and RepeatingModel's untrimmed final segment. */
public final class PillarBodyPartRenderer<T extends OwnedBodyPartEntity> extends EntityRenderer<T, PillarBodyPartRenderer.State> {
    private static final Identifier HORN = RipplesFabric.id("textures/entity/projectiles/pm_horn.png");
    private static final Identifier RIBS = RipplesFabric.id("textures/entity/projectiles/pillarman_ribs.png");
    private final ModelPart hornTip = part(-1, -1, -1, 2, 1, 2);
    private final ModelPart ribTip = part(-.5F, -.5F, -.5F, 1, 1, 8);
    private final ModelPart repeated = part(-1, -1, 1, 2, 1, 8);
    private final Identifier texture;
    private final boolean longTip;
    public static final class State extends EntityRenderState { Vec3 extent = Vec3.ZERO; boolean rib, visible; }
    public PillarBodyPartRenderer(EntityRendererProvider.Context context) { this(context, null, false); }
    public PillarBodyPartRenderer(EntityRendererProvider.Context context, Identifier texture, boolean longTip) { super(context); this.texture = texture; this.longTip = longTip; }
    public static void initialize() {
        EntityRendererRegistry.register(PillarBodyParts.HORN, PillarBodyPartRenderer::new);
        EntityRendererRegistry.register(PillarBodyParts.RIBS, PillarBodyPartRenderer::new);
    }
    @Override public State createRenderState() { return new State(); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(T entity, float partialTick) { return entity.segmentBounds(); }
    @Override public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.extent = entity.getPosition(partialTick).subtract(entity.bodyOrigin(partialTick)); state.rib = longTip || entity instanceof PillarRibsEntity;
        var viewer = Minecraft.getInstance().player;
        state.visible = !entity.isInvisible() || viewer != null && !entity.isInvisibleTo(viewer);
        if (entity.livingOwner() != null) state.lightCoords = entityRenderDispatcher.getPackedLightCoords(entity.livingOwner(), partialTick);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) return;
        double horizontal = state.extent.horizontalDistance();
        float yaw = (float) Math.toDegrees(Math.atan2(-state.extent.x, state.extent.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(state.extent.y, horizontal));
        float length = (float) state.extent.length();
        var renderType = RenderTypes.entityTranslucent(texture != null ? texture : state.rib ? RIBS : HORN);
        pose.pushPose(); pose.scale(1, -1, -1); pose.rotateDegrees(Axis.YP, yaw); pose.rotateDegrees(Axis.XP, pitch);
        if (length >= 2F / 16) {
            collector.submitModelPart(state.rib ? ribTip : hornTip, pose, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
            length -= 2F / 16;
        }
        // The original always draws one final full repeating piece, even when less than eight pixels remain.
        while (length >= 8F / 16) {
            collector.submitModelPart(repeated, pose, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
            length -= 8F / 16; pose.translate(0, 0, 8F / 16);
        }
        collector.submitModelPart(repeated, pose, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }
    private static ModelPart part(float x, float y, float z, float width, float height, float depth) {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, width, height, depth), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("part");
    }
}
