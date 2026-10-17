package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.client.StarPlatinumRenderer;
import com.github.standobyte.jojo.fabric.client.TheWorldRenderer;
import com.github.standobyte.jojo.fabric.client.model.StarPlatinumRenderState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.EntityModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual mouse pitch while attacking, authoritative elevated hits, and baked model aim in both views. */
public final class StandAimClientGameTest implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 144, 0.5);

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            exercise(context, world.getServer(), "singleplayer");
        }
        var properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (var server = context.worldBuilder().createServer(properties)) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                exercise(context, server, "dedicated");
            }
        }
    }

    private static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        var uuid = context.computeOnClient(client -> client.player.getUUID());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.CREATIVE);
            player.setNoGravity(true);
            player.connection.teleport(START.x, START.y, START.z, 0, 0);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
                player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
        });
        context.waitFor(client -> client.player.position().distanceTo(START) < 0.01);
        context.runOnClient(client -> {
            client.options.sensitivity().set(0.5);
            client.options.invertMouseX().set(false);
            client.options.invertMouseY().set(false);
            client.options.smoothCamera = false;
            client.options.bobView().set(false);
        });
        for (boolean world : new boolean[] {false, true}) {
            server.runOnServer(minecraft -> StandService.grant(minecraft.getPlayerList().getPlayer(uuid),
                    world ? StandProgress.THE_WORLD : StandProgress.STAR_PLATINUM));
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld() == world);
            context.waitTicks(6); // Original summon toggle debounce survives replacing the granted Stand.
            context.getInput().pressKey(key(context, "summon"));
            context.waitFor(client -> StandView.ownStand(client) != null && StandView.ownStand(client).isTheWorld() == world);
            for (boolean thirdPerson : new boolean[] {false, true}) {
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                context.waitTicks(3);
                context.getInput().lookAt(0, -60);
                context.waitTicks(3);
                if (thirdPerson) {
                    context.getInput().pressKey(options -> options.keyTogglePerspective);
                    context.waitFor(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
                }
                for (float pitch : new float[] {-60, 60}) {
                    int targetId = server.computeOnServer(minecraft -> {
                        var player = minecraft.getPlayerList().getPlayer(uuid);
                        var target = EntityTypes.CHICKEN.create(player.level(), EntitySpawnReason.COMMAND);
                        target.setNoAi(true); target.setNoGravity(true);
                        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                        target.setHealth(1000);
                        target.snapTo(player.getEyePosition().add(Vec3.directionFromRotation(pitch, 0).scale(1.7))
                                .subtract(0, target.getBbHeight() / 2.0, 0), 180, 0);
                        player.level().addFreshEntity(target);
                        return target.getId();
                    });
                    context.waitFor(client -> client.level.getEntity(targetId) != null);
                    if (pitch < 0) context.getInput().holdKey(key(context, "barrage"));
                    else {
                        // Continue holding the attack. Real MouseHandler deltas must update ongoing server aim.
                        context.getInput().moveCursor(0, 0);
                        context.getInput().moveCursor(0, 800);
                    }
                    server.waitFor(minecraft -> ((LivingEntity) minecraft.getPlayerList().getPlayer(uuid).level()
                            .getEntity(targetId)).getHealth() < 1000);
                    context.waitFor(client -> Math.abs(StandView.ownStand(client).getXRot() - pitch) < 2
                            && StandView.ownStand(client).actionPhase() == StarPlatinumEntity.PHASE_BARRAGE);
                    context.runOnClient(client -> {
                        var stand = StandView.ownStand(client);
                        var state = (StarPlatinumRenderState) client.getEntityRenderDispatcher().extractEntity(stand, 1);
                        if (Math.abs(state.xRot - pitch) > 2) throw new AssertionError("Renderer lost synchronized attack pitch: " + state.xRot);
                        if (Math.signum(stand.getY() - client.player.getY()) != -Math.signum(pitch))
                            throw new AssertionError("Manifestation stayed horizontal instead of following the attack's elevation");
                        var renderer = client.getEntityRenderDispatcher().getRenderer(stand);
                        EntityModel<StarPlatinumRenderState> model = renderer instanceof TheWorldRenderer theWorld
                                ? theWorld.getModel() : ((StarPlatinumRenderer) renderer).getModel();
                        model.setupAnim(state);
                        float armPitch = model.root().getChild("body").getChild("upperPart").getChild("rightArmXRot").xRot;
                        if (Math.abs(armPitch - Math.toRadians(pitch)) > 0.04)
                            throw new AssertionError("Baked barrage arms ignored look pitch: " + armPitch + ", phase="
                                    + state.actionPhase + ", elapsed=" + state.actionTicks + ", pitch=" + state.xRot);
                        verifyPunchModels(model, world);
                        model.setupAnim(state);
                    });
                    context.takeScreenshot("stand-aim-" + (world ? "world" : "platinum") + "-" + (thirdPerson ? "f5" : "first")
                            + "-" + (pitch < 0 ? "up" : "down") + "-" + mode);
                    server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(uuid).level().getEntity(targetId).discard());
                }
                context.getInput().releaseKey(key(context, "barrage"));
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(uuid)).allowsStaminaRegen());
                context.waitTicks(25);
            }
            context.getInput().pressKey(key(context, "summon"));
            context.waitFor(client -> StandView.ownStand(client) == null);
        }
    }

    private static void verifyPunchModels(EntityModel<StarPlatinumRenderState> model, boolean world) {
        for (boolean heavy : new boolean[] {false, true}) for (int ability = 0; ability <= (world ? 2 : 1); ability++) {
            var state = new StarPlatinumRenderState();
            state.actionPhase = StarPlatinumRenderState.PHASE_PERFORM;
            state.actionDuration = 10; state.actionTicks = 1;
            state.heavyAttack = heavy; state.abilityKind = ability;
            var pivot = world && ability == 1 ? model.root().getChild("body").getChild("rightLegXRot")
                    : model.root().getChild("body").getChild("upperPart").getChild("rightArmXRot");
            state.xRot = 0; model.setupAnim(state); float flat = pivot.xRot;
            state.xRot = -60; model.setupAnim(state); float up = pivot.xRot;
            state.xRot = 60; model.setupAnim(state); float down = pivot.xRot;
            if (Math.abs(up - flat + Math.PI / 3) > 0.001 || Math.abs(down - flat - Math.PI / 3) > 0.001)
                throw new AssertionError("Punch/kick baked limb stayed horizontal: ability=" + ability + ", heavy=" + heavy);
            state.actionPhase = StarPlatinumRenderState.PHASE_IDLE;
            model.setupAnim(state);
            if (Math.abs(pivot.xRot) > 0.001) throw new AssertionError("Attack pitch leaked into the next idle pose");
        }
    }

    private static KeyMapping key(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> KeyMapping.get("key.jojo.fabric." + name));
    }
}
