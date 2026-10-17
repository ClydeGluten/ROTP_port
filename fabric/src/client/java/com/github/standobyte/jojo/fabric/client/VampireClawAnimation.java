package com.github.standobyte.jojo.fabric.client;

import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Original vampire_claws keyframes, 1.75 playback speed, handedness and OUTCUBIC fade. */
public final class VampireClawAnimation {
    private static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] CHANNELS = {"x", "y", "z", "pitch", "yaw", "roll", "bend"};
    private static final Map<String, float[][]> CLIP = load();
    private static Map<String, float[][]> load() {
        var result = new HashMap<String, float[][]>();
        for (String part : PARTS) result.put(part, new float[7][21]);
        try (var stream = VampireClawAnimation.class.getResourceAsStream("/assets/jojo/player_animation/vampire/vampire_claws.json")) {
            if (stream == null) throw new IllegalStateException("Missing original vampire_claws animation");
            var moves = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("emote").getAsJsonArray("moves");
            for (var value : moves) {
                var move = value.getAsJsonObject(); int tick = move.get("tick").getAsInt();
                if (tick < 1 || tick > 20) continue;
                for (String part : PARTS) if (move.has(part)) {
                    var channels = move.getAsJsonObject(part);
                    for (int i = 0; i < CHANNELS.length; i++) if (channels.has(CHANNELS[i])) result.get(part)[i][tick] = channels.get(CHANNELS[i]).getAsFloat();
                }
            }
            // Emote's zero frame uses the vanilla limb pivots; all rotation/bend channels begin at zero.
            result.get("rightArm")[0][0] = -5; result.get("rightArm")[1][0] = 2;
            result.get("leftArm")[0][0] = 5; result.get("leftArm")[1][0] = 2;
            result.get("rightLeg")[0][0] = -1.9F; result.get("rightLeg")[1][0] = 12;
            result.get("leftLeg")[0][0] = 1.9F; result.get("leftLeg")[1][0] = 12;
            return result;
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load original vampire_claws animation", error); }
    }
    public static void apply(PlayerModel model, AvatarRenderState state) {
        VampireBendGeometry.reset(model);
        if (!(state instanceof VampireRenderData data)) return;
        var visual = data.jojo$vampireVisual();
        if (visual.clawTick() <= 0 && visual.fadeTicks() <= 0) return;
        float partial = data.jojo$vampirePartialTick();
        float tick = visual.clawTick() > 0 ? Mth.clamp((visual.clawTick() - 1 + partial) * 1.75F, 0, 20) : 20;
        float fade = visual.clawTick() > 0 ? 1 : (float) Math.pow(Math.clamp((visual.fadeTicks() - partial) / 17F, 0, 1), 3);
        applyPart(model.head, "head", tick, fade, visual.leftHanded(), false);
        applyPart(model.body, "torso", tick, fade, visual.leftHanded(), true);
        applyPart(model.rightArm, visual.leftHanded() ? "leftArm" : "rightArm", tick, fade, visual.leftHanded(), false);
        applyPart(model.leftArm, visual.leftHanded() ? "rightArm" : "leftArm", tick, fade, visual.leftHanded(), false);
        applyPart(model.rightLeg, visual.leftHanded() ? "leftLeg" : "rightLeg", tick, fade, visual.leftHanded(), false);
        applyPart(model.leftLeg, visual.leftHanded() ? "rightLeg" : "leftLeg", tick, fade, visual.leftHanded(), false);
        // Modern sleeves/pants/jacket are children of their corresponding part and inherit its pose.
        bend(model.body, model.jacket, "torso", tick, fade, true);
        bend(model.rightArm, model.rightSleeve, visual.leftHanded() ? "leftArm" : "rightArm", tick, fade, false);
        bend(model.leftArm, model.leftSleeve, visual.leftHanded() ? "rightArm" : "leftArm", tick, fade, false);
        bend(model.rightLeg, model.rightPants, visual.leftHanded() ? "leftLeg" : "rightLeg", tick, fade, false);
        bend(model.leftLeg, model.leftPants, visual.leftHanded() ? "rightLeg" : "leftLeg", tick, fade, false);
    }
    private static void applyPart(ModelPart part, String name, float tick, float fade, boolean mirror, boolean torso) {
        float[][] clip = CLIP.get(name); float sign = mirror ? -1 : 1;
        part.x = Mth.lerp(fade, part.x, sample(clip[0], tick) * sign);
        part.y = Mth.lerp(fade, part.y, sample(clip[1], tick));
        part.z = Mth.lerp(fade, part.z, sample(clip[2], tick));
        part.xRot = Mth.lerp(fade, part.xRot, sample(clip[3], tick));
        part.yRot = Mth.lerp(fade, Mth.wrapDegrees(part.yRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, sample(clip[4], tick) * sign);
        part.zRot = Mth.lerp(fade, part.zRot, sample(clip[5], tick) * sign);
    }
    private static void bend(ModelPart part, ModelPart outer, String name, float tick, float fade, boolean upper) {
        float bend = sample(CLIP.get(name)[6], tick) * fade;
        float pivot = name.endsWith("Arm") ? 4 : 6;
        VampireBendGeometry.set(part, pivot, bend, upper); VampireBendGeometry.set(outer, pivot, bend, upper);
    }
    private static float sample(float[] values, float tick) {
        int index = (int) tick; return Mth.lerp(tick - index, values[index], values[Math.min(index + 1, 20)]);
    }
    private VampireClawAnimation() { }
}
