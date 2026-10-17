package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireCombatService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Original FrozenLayer and HamonBurnLayer.TextureSize, using modern model submission. */
public final class FrozenLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    private static final Map<ModelPart.Cube, int[]> CUBE_TEXTURES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntityRenderState, Integer> LEVELS = new WeakHashMap<>();
    private int[] textureSize;
    public FrozenLayer(RenderLayerParent<S, M> parent) { super(parent); }
    /** Modern Model has no texture dimensions; capture the same dimensions supplied to its baked cubes. */
    public static void rememberCube(ModelPart.Cube cube, float width, float height) { CUBE_TEXTURES.put(cube, new int[] {(int) width, (int) height}); }
    public static void extract(LivingEntity entity, LivingEntityRenderState state) {
        var effect = entity.getEffect(VampireCombatService.FREEZE);
        LEVELS.put(state, effect == null ? -1 : Math.min(effect.getAmplifier(), 3));
    }
    private int[] size() {
        if (textureSize == null) {
            int[][] found = {null};
            getParentModel().root().visit(new PoseStack(), (pose, path, index, cube) -> {
                if (found[0] == null) found[0] = CUBE_TEXTURES.get(cube);
            });
            textureSize = found[0] == null ? new int[] {64, 32} : found[0];
        }
        return textureSize;
    }
    public static Identifier texture(int amplifier, boolean biped, int width, int height) {
        int widthLog = Mth.clamp(Mth.ceillog2(width), 6, 8);
        int heightLog = Mth.clamp(Mth.ceillog2(height), widthLog - 1, widthLog);
        return RipplesFabric.id("textures/entity/layer/vampire_freeze/" + (biped ? "biped/" : "")
                + "t" + (1 << widthLog) + "x" + (1 << heightLog) + "/" + (Math.min(amplifier, 3) + 1) + ".png");
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        int amplifier = LEVELS.getOrDefault(state, -1);
        if (amplifier < 0 || state.isInvisible) return;
        int[] size = size();
        collector.submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucent(texture(amplifier,
                getParentModel() instanceof HumanoidModel<?>, size[0], size[1])), light, LivingEntityRenderer.getOverlayCoords(state, 0), -1);
    }
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart arm) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.isInvisible()) return;
        var freeze = player.getEffect(VampireCombatService.FREEZE);
        if (freeze == null) return;
        collector.submitModelPart(arm, pose, RenderTypes.entityTranslucent(texture(freeze.getAmplifier(), true, 64, 64)),
                light, OverlayTexture.NO_OVERLAY, null);
    }
}
