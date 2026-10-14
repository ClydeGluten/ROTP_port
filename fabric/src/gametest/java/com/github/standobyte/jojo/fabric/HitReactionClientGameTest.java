package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Hits through real client keys and checks mob displacement after network synchronization. */
public final class HitReactionClientGameTest implements FabricClientGameTest {
    private static final Vec3 TARGET = new Vec3(0.5, 140, 2.5);

    @Override public void runTest(ClientGameTestContext context) {
        var previousHeavy = context.computeOnClient(client -> InputConstants.getKey(KeyMapping.get("key.jojo.fabric.heavy").saveString()));
        context.runOnClient(client -> {
            KeyMapping.get("key.jojo.fabric.heavy").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F6));
            KeyMapping.resetMapping();
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
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
        } finally {
            context.getInput().releaseKey(KeyMapping.get("key.jojo.fabric.barrage"));
            context.runOnClient(client -> {
                KeyMapping.get("key.jojo.fabric.heavy").setKey(previousHeavy);
                KeyMapping.resetMapping();
            });
        }
    }

    private static void exercise(ClientGameTestContext context, TestServerContext server, String mode) {
        var id = context.computeOnClient(client -> client.player.getUUID());
        server.runOnServer(minecraft -> {
            var player = minecraft.getPlayerList().getPlayer(id);
            player.setGameMode(GameType.CREATIVE);
            player.setNoGravity(true);
            player.connection.teleport(0.5, 140, 0.5, 0, 15);
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
            }
        });
        context.waitFor(client -> client.player.getY() > 139);
        context.getInput().lookAt(0, 15);
        for (String standId : new String[] {StandProgress.STAR_PLATINUM, StandProgress.THE_WORLD}) {
            server.runOnServer(minecraft -> {
                var player = minecraft.getPlayerList().getPlayer(id);
                StandService.dismiss(player);
                player.setAttached(StandAttachments.PROGRESS, StandProgress.grant(standId, true));
                if (!StandService.toggle(player)) throw new AssertionError("Could not summon test Stand");
            });
            context.waitFor(client -> client.player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(standId));
            for (String action : new String[] {"light", "heavy", "barrage"}) {
                server.waitFor(minecraft -> StandService.current(minecraft.getPlayerList().getPlayer(id)).actionPhase() == 0);
                int cowId = server.computeOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayer(id);
                    var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
                    cow.snapTo(TARGET, 0, 0);
                    cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                    cow.setHealth(100);
                    cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                    player.level().addFreshEntity(cow);
                    return cow.getId();
                });
                context.waitFor(client -> client.level.getEntity(cowId) instanceof Cow cow && cow.getHealth() == 100);
                var key = KeyMapping.get("key.jojo.fabric." + action);
                if (action.equals("barrage")) context.getInput().holdKey(key);
                else context.getInput().pressKey(key);
                server.waitFor(minecraft -> ((Cow) minecraft.getPlayerList().getPlayer(id).level().getEntity(cowId)).getHealth() < 100);
                if (action.equals("barrage")) context.getInput().releaseKey(key);
                // Speed is still zero: visible displacement here can only come from the hit's impulse.
                context.waitFor(client -> client.level.getEntity(cowId) instanceof Cow cow
                        && cow.getHealth() < 100 && cow.position().subtract(TARGET).horizontalDistanceSqr() > 0.001);
                server.runOnServer(minecraft -> {
                    var cow = (Cow) minecraft.getPlayerList().getPlayer(id).level().getEntity(cowId);
                    cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
                });
                server.waitFor(minecraft -> ((Cow) minecraft.getPlayerList().getPlayer(id).level().getEntity(cowId)).isPanicking());
                if (action.equals("barrage")) context.takeScreenshot("hit-reaction-" + mode + "-" + standId.substring(5));
                server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayer(id).level().getEntity(cowId).discard());
            }
        }
        server.runOnServer(minecraft -> StandService.dismiss(minecraft.getPlayerList().getPlayer(id)));
    }
}
