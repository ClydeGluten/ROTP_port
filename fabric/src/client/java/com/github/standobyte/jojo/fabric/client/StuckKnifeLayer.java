package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Original player-only KnifeLayer, including the 1.16 PlayerModel part order and seeded placement. */
public final class StuckKnifeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final RenderStateDataKey<Integer> COUNT = RenderStateDataKey.create(() -> "jojo:stuck_knives");
    private static final Identifier TEXTURE = RipplesFabric.id("textures/entity/projectiles/knife.png");
    private final Part[] parts;
    private final ModelPart ear = new ModelPart(List.of(), Map.of());
    private final ModelPart cloak = new ModelPart(List.of(), Map.of());

    private record Part(int anchor, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }
    private static final int EAR = 0, CLOAK = 1, LEFT_ARM = 2, RIGHT_ARM = 3, LEFT_LEG = 4, RIGHT_LEG = 5, BODY = 6;

    public StuckKnifeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, boolean slim) {
        super(parent);
        Part leftArm = new Part(LEFT_ARM, -1, -2, -2, slim ? 2 : 3, 10, 2);
        Part rightArm = new Part(RIGHT_ARM, slim ? -2 : -3, -2, -2, 1, 10, 2);
        Part leftLeg = new Part(LEFT_LEG, -2, 0, -2, 2, 12, 2);
        Part rightLeg = new Part(RIGHT_LEG, -2, 0, -2, 2, 12, 2);
        Part ear = new Part(EAR, -3, -6, -1, 3, 0, 0);
        Part cloak = new Part(CLOAK, -5, 0, -1, 5, 16, 0);
        Part jacket = new Part(BODY, -4, 0, -2, 4, 12, 2);
        // PlayerModel's constructor resets the list populated by HumanoidModel. Its new
        // ear, cloak, replacement limbs and clothing are the legacy random-part pool.
        // Cube bounds exclude deformation, so sleeves/pants use their underlying bounds.
        parts = slim
                ? new Part[] {ear, cloak, leftArm, rightArm, leftArm, rightArm, leftLeg, leftLeg, rightLeg, jacket}
                : new Part[] {ear, cloak, leftArm, leftArm, rightArm, leftLeg, leftLeg, rightLeg, jacket};
    }

    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                                 AvatarRenderState state, float yaw, float pitch) {
        int count = ((FabricRenderState) state).getDataOrDefault(COUNT, 0);
        if (count <= 0) return;
        PlayerModel model = getParentModel();
        // In 1.16 the ear pose is copied by renderEars only for the special player skin.
        if (state.showExtraEars) {
            ear.xRot = model.head.xRot;
            ear.yRot = model.head.yRot;
            ear.zRot = model.head.zRot;
            ear.z = model.head.z;
        }
        boolean chestEmpty = state.chestEquipment.isEmpty();
        cloak.y = chestEmpty ? state.isCrouching ? 1.85F : 0 : state.isCrouching ? 0.8F : -0.85F;
        cloak.z = chestEmpty ? state.isCrouching ? 1.4F : 0 : state.isCrouching ? 0.3F : -1.1F;
        Random random = new Random(state.id);
        for (int i = 0; i < count; i++) {
            Part part = parts[random.nextInt(parts.length)];
            // Each original part has one cube; nextInt(1) still advances java.util.Random.
            random.nextInt(1);
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            pose.pushPose();
            anchor(model, part.anchor).translateAndRotate(pose);
            // Current player arm poses use y=2 for both skins; the legacy slim model used 2.5.
            // Stay attached to the actual current posed limb rather than shifting off the model.
            pose.translate(Mth.lerp(x, part.minX, part.maxX) / 16F,
                    Mth.lerp(y, part.minY, part.maxY) / 16F, Mth.lerp(z, part.minZ, part.maxZ) / 16F);
            x = -(x * 2 - 1); y = -(y * 2 - 1); z = -(z * 2 - 1);
            float horizontal = Mth.sqrt(x * x + z * z);
            float knifeYaw = (float) (Math.atan2(x, z) * (double) (180F / (float) Math.PI));
            float knifePitch = (float) (Math.atan2(y, horizontal) * (double) (180F / (float) Math.PI));
            KnifeRenderer.submitKnife(pose, collector, TEXTURE, knifeYaw, knifePitch, light);
            pose.popPose();
        }
    }

    private ModelPart anchor(PlayerModel model, int anchor) {
        return switch (anchor) {
            case EAR -> ear;
            case CLOAK -> cloak;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
            case BODY -> model.body;
            default -> throw new IllegalArgumentException("Unknown knife anchor " + anchor);
        };
    }
}
