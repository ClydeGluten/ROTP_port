package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.stand.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Original BlockShardRenderer (two 64-cuboid caches, UUID dimensions/UV/Euler angles) and LotsOfBlocksBrokenPacket receiver. */
public final class StandBlockShardRenderer extends EntityRenderer<StandBlockShardEntity, StandBlockShardRenderer.State> {
    private static boolean initialized;
    private final ModelPart[] models = new ModelPart[64];
    private final ModelPart[] modelsGlass = new ModelPart[64];

    public static void initialize() {
        if (initialized) return; initialized = true;
        EntityRendererRegistry.register(StandHeavyImpact.SHARD, StandBlockShardRenderer::new);
        StandBlockShardEntity.setClientBreak(StandBlockShardRenderer::onShardBreak);
        ClientPlayNetworking.registerGlobalReceiver(StandHeavyImpact.ShockwavePayload.TYPE, (payload, context) ->
                context.client().execute(() -> receiveShockwave(context.client(), payload)));
    }

    public static final class State extends EntityRenderState {
        boolean visible;
        ModelPart model;
        RenderType renderType;
        float xRot, yRot, zRot;
    }

    public StandBlockShardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StandBlockShardEntity entity, State state, float partial) {
        if (TimeStopClient.isFrozen(entity)) partial = 0;
        super.extractRenderState(entity, state, partial);
        var client = Minecraft.getInstance();
        state.visible = !entity.isInvisible() || !entity.isInvisibleTo(client.player);
        var blockState = entity.block();
        if (!state.visible || blockState == null) {
            state.model = null; state.renderType = null; return;
        }
        state.model = getModelPart(entity, SourceEraMaterials.glass(blockState));
        long randomNum = entity.getUUID().getMostSignificantBits() >> 6;
        state.xRot = (float) Math.PI * (int) (randomNum & 127) / 64;
        randomNum >>= 7;
        state.yRot = (float) Math.PI * (int) (randomNum & 127) / 64;
        randomNum >>= 7;
        state.zRot = (float) Math.PI * (int) (randomNum & 127) / 64;
        var material = client.getModelManager().getBlockStateModelSet().getParticleMaterial(blockState);
        var sprite = material == null ? null : material.sprite();
        if (sprite != null && sprite.contents() != null && sprite.contents().name() != null && !sprite.contents().name().getPath().equals("missingno")) {
            Identifier name = sprite.contents().name();
            Identifier texture = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
            ChunkSectionLayer layer = material.forceTranslucent() ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.byTransparency(sprite.transparency());
            state.renderType = switch (layer) {
                case SOLID -> RenderTypes.entitySolid(texture);
                case CUTOUT -> RenderTypes.entityCutout(texture);
                case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
            };
        } else {
            state.renderType = null;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.visible && state.model != null && state.renderType != null) {
            pose.pushPose();
            pose.scale(-1, -1, 1);
            state.model.xRot = state.xRot;
            state.model.yRot = state.yRot;
            state.model.zRot = state.zRot;
            collector.submitModelPart(state.model, pose, state.renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.outlineColor);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    private ModelPart getModelPart(StandBlockShardEntity entity, boolean glass) {
        long randomNum = entity.getUUID().getMostSignificantBits();
        int x = (int) (randomNum & 3);
        randomNum >>= 2;
        int y = (int) (randomNum & 3);
        randomNum >>= 2;
        int z = (int) (randomNum & 3);
        int index = x * 16 + y * 4 + z;
        ModelPart[] cache = glass ? modelsGlass : models;
        if (cache[index] == null) {
            int uv = (int) ((randomNum >> 27) & 255);
            int u = uv & 15;
            int v = (uv >> 4) & 15;
            float xSize = x + 5;
            float ySize = y + 5;
            float zSize = glass ? 1 : z + 5;
            var mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("shard", CubeListBuilder.create().texOffs(u, v)
                    .addBox(-xSize / 2, -ySize / 2, -zSize / 2, xSize, ySize, zSize), PartPose.offset(0, -3, 0));
            cache[index] = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("shard");
        }
        return cache[index];
    }

    private static void onShardBreak(StandBlockShardEntity entity) {
        if (!(entity.level() instanceof ClientLevel level)) return;
        var blockState = entity.block();
        if (blockState == null) return;
        Vec3 pos = entity.position();
        var soundType = blockState.getSoundType();
        var sound = soundType.getBreakSound();
        if (sound != null) {
            level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false);
        }
        BlockPos blockPos = BlockPos.containing(pos);
        if (!blockState.isAir()) {
            var engine = Minecraft.getInstance().particleEngine;
            for (int i = 0; i < 4; i++) {
                double x = (Math.random() - 0.5) * 0.2;
                double y = (Math.random() - 0.5) * 0.2;
                double z = (Math.random() - 0.5) * 0.2;
                engine.add(new TerrainParticle(level, pos.x + x, pos.y + y, pos.z + z, x * 0.25, y * 0.25, z * 0.25, blockState, blockPos));
            }
        }
    }

    private static void receiveShockwave(Minecraft client, StandHeavyImpact.ShockwavePayload payload) {
        if (client.level == null) return;
        Stream<StandHeavyImpact.BlockVisual> stream = payload.blocks().stream();
        if (payload.blocks().size() > 128) {
            Vec3 cameraPos = client.gameRenderer.mainCamera().position();
            stream = stream.sorted(Comparator.comparingDouble(block -> block.pos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z))).limit(128);
        }
        var selected = stream.toList();
        int particlesSetting = client.options.particles().get().ordinal();
        for (int i = 0; i < selected.size(); i++) {
            var visual = selected.get(i);
            var blockPos = visual.pos();
            var blockState = Block.stateById(visual.state());
            if (!blockState.isAir()) {
                if (particlesSetting < 2 && (particlesSetting < 1 || i % 2 == 0)) {
                    client.level.addDestroyBlockEffect(blockPos, blockState);
                }
                if (i % 8 == 0) {
                    var soundType = blockState.getSoundType();
                    client.level.playLocalSound(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                            soundType.getBreakSound(), SoundSource.BLOCKS,
                            (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, false);
                }
            }
        }
    }
}
