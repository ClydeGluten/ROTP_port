package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.ZombieAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/** Original sated zombie overlay, disguise eyes and shared KosmX claw animation. */
public final class ZombieLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final Identifier TEXTURE = RipplesFabric.id("textures/entity/biped/sated_zombie.png");
    private static final Identifier EYES = RipplesFabric.id("textures/entity/biped/vampire_eyes.png");
    private record Snapshot(ZombieAttachments.Visual visual, float partial, boolean spectator) { }
    private static final Map<AvatarRenderState, Snapshot> STATES = new WeakHashMap<>();
    private static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] CHANNELS = {"x", "y", "z", "pitch", "yaw", "roll", "bend"};
    private static final Map<String, float[][]> CLIP = loadClip();
    public ZombieLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }
    public static void extract(Avatar player, AvatarRenderState state, float partial) {
        STATES.put(state, new Snapshot(player.getAttachedOrCreate(ZombieAttachments.VISUAL),
                TimeStopClient.isFrozen(player) ? 0 : partial, player.isSpectator()));
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var data = STATES.get(state);
        if (data == null || state.isInvisible || data.spectator) return;
        if (data.visual.skin()) collector.submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucent(TEXTURE),
                light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
        if (data.visual.glowing()) collector.submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucent(EYES),
                0xF000F0, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
    }
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, int light) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible() || player.isSpectator() || !arm.visible
                || !player.getAttachedOrCreate(ZombieAttachments.VISUAL).skin()) return;
        collector.submitModelPart(arm, pose, RenderTypes.entityTranslucent(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
    }
    /** Same upstream clip at 1.75 speed, but the zombie starts its 17-tick fade at tick eight. */
    public static void applyAnimation(PlayerModel model, AvatarRenderState state) {
        var snapshot = STATES.get(state);
        if (snapshot == null || snapshot.spectator || snapshot.visual.clawTicks() <= 0 && snapshot.visual.fadeTicks() <= 0) return;
        var visual = snapshot.visual;
        float elapsed = visual.clawTicks() > 0 ? visual.clawTicks() - 1 + snapshot.partial : 8 + 17 - visual.fadeTicks() + snapshot.partial;
        float tick = Math.clamp(elapsed * 1.75F, 0, 20);
        float fade = visual.clawTicks() > 0 ? 1 : (float) Math.pow(Math.clamp((visual.fadeTicks() - snapshot.partial) / 17F, 0, 1), 3);
        boolean mirror = visual.leftHanded();
        VampireBendGeometry.reset(model);
        applyPart(model.head, null, "head", tick, fade, mirror);
        applyPart(model.body, model.jacket, "torso", tick, fade, mirror);
        applyPart(model.rightArm, model.rightSleeve, mirror ? "leftArm" : "rightArm", tick, fade, mirror);
        applyPart(model.leftArm, model.leftSleeve, mirror ? "rightArm" : "leftArm", tick, fade, mirror);
        applyPart(model.rightLeg, model.rightPants, mirror ? "leftLeg" : "rightLeg", tick, fade, mirror);
        applyPart(model.leftLeg, model.leftPants, mirror ? "rightLeg" : "leftLeg", tick, fade, mirror);
    }
    private static void applyPart(ModelPart part, ModelPart outer, String name, float tick, float fade, boolean mirror) {
        var values = CLIP.get(name); float sign = mirror ? -1 : 1;
        part.x = Mth.lerp(fade, part.x, sample(values[0], tick) * sign);
        part.y = Mth.lerp(fade, part.y, sample(values[1], tick));
        part.z = Mth.lerp(fade, part.z, sample(values[2], tick));
        part.xRot = Mth.lerp(fade, part.xRot, sample(values[3], tick));
        part.yRot = Mth.lerp(fade, Mth.wrapDegrees(part.yRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, sample(values[4], tick) * sign);
        part.zRot = Mth.lerp(fade, part.zRot, sample(values[5], tick) * sign);
        if (outer != null) {
            float bend = sample(values[6], tick) * fade, pivot = name.endsWith("Arm") ? 4 : 6;
            VampireBendGeometry.set(part, pivot, bend, name.equals("torso"));
            VampireBendGeometry.set(outer, pivot, bend, name.equals("torso"));
        }
    }
    private static float sample(float[] values, float tick) {
        int index = (int) tick; return Mth.lerp(tick - index, values[index], values[Math.min(index + 1, 20)]);
    }
    private static Map<String, float[][]> loadClip() {
        var result = new java.util.HashMap<String, float[][]>();
        for (var part : PARTS) result.put(part, new float[7][21]);
        try (var stream = ZombieLayer.class.getResourceAsStream("/assets/jojo/player_animation/vampire/vampire_claws.json")) {
            if (stream == null) throw new IllegalStateException("Missing original zombie claw animation (vampire_claws)");
            var moves = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("emote").getAsJsonArray("moves");
            for (var value : moves) {
                var move = value.getAsJsonObject(); int tick = move.get("tick").getAsInt();
                if (tick < 1 || tick > 20) continue;
                for (var part : PARTS) if (move.has(part)) {
                    var channels = move.getAsJsonObject(part);
                    for (int i = 0; i < CHANNELS.length; i++) if (channels.has(CHANNELS[i])) result.get(part)[i][tick] = channels.get(CHANNELS[i]).getAsFloat();
                }
            }
            result.get("rightArm")[0][0] = -5; result.get("rightArm")[1][0] = 2;
            result.get("leftArm")[0][0] = 5; result.get("leftArm")[1][0] = 2;
            result.get("rightLeg")[0][0] = -1.9F; result.get("rightLeg")[1][0] = 12;
            result.get("leftLeg")[0][0] = 1.9F; result.get("leftLeg")[1][0] = 12;
            return result;
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load original zombie claw animation", e); }
    }
}
