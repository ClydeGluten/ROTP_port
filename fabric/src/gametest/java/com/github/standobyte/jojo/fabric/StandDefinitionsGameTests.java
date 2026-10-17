package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandAllocation;
import com.github.standobyte.jojo.core.StandDefinitions;
import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.ResolveProgress;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.util.UUID;

/** Regression at the save/attachment/factory boundary, not a second copy of definition constants. */
public final class StandDefinitionsGameTests {
    @GameTest
    public void legacyOwnershipAndProgressSurviveGeneralizedDefinitions(GameTestHelper test) {
        var level = test.getLevel();
        var server = level.getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "stand-definition"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);
        try {
            var oldSave = JsonParser.parseString("{\"stand\":\"jojo:star_platinum\",\"stamina\":300,\"development\":0.5,\"had_stand\":true}");
            var oldPower = StandAttachments.CODEC.parse(JsonOps.INSTANCE, oldSave).getOrThrow();
            player.setAttached(StandAttachments.PROGRESS, oldPower);
            player.setAttached(StandAttachments.RESOLVE, ResolveProgress.EMPTY.withLevel(2));
            test.assertTrue(StandService.toggle(player), "Old SP save still summons its original manifestation");
            test.assertValueEqual(StandService.currentManifestation(player).definition().id(), StandProgress.STAR_PLATINUM, "Factory returns the saved Stand");
            player.setGameMode(GameType.SURVIVAL);
            StandService.grant(player, StandProgress.THE_WORLD);
            ProgressionService.setLevel(player, 3);
            StandService.grant(player, StandProgress.STAR_PLATINUM);
            test.assertValueEqual(ProgressionService.state(player).level(), 2, "SP progression survives switching Stand");
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.THE_WORLD_RESOLVE).level(), 3, "Legacy TW attachment remains separate");
            var beforeInvalidGrant = player.getAttachedOrCreate(StandAttachments.PROGRESS);
            boolean rejected = false;
            try { StandService.grant(player, "jojo:unknown_stand"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            test.assertTrue(rejected && beforeInvalidGrant.equals(player.getAttachedOrCreate(StandAttachments.PROGRESS)), "Unknown grant cannot destroy current ownership");

            player.setGameMode(GameType.CREATIVE);
            // Known source data can be decoded before its module ships, but must not become SP or gain time actions.
            player.setAttached(StandAttachments.PROGRESS, new StandProgress(StandProgress.BOY_II_MAN, 0, 0, true));
            test.assertTrue(!StandService.toggle(player) && StandService.currentManifestation(player) == null, "Non-manifesting definition never creates an SP substitute");
            test.assertTrue(!TimeStopService.toggle(player) && !TheWorldActions.blink(player), "Creative mode cannot bypass Stand time capability");
            test.assertValueEqual(ProgressionService.timeStopMaximum(player), 0, "Non-time-stopper has no duration");
            test.assertValueEqual(StandAllocation.DEFAULT_CATALOG, StandDefinitions.naturalPool(), "Natural allocation uses integrated source pool");
            test.assertTrue(StandAllocation.DEFAULT_CATALOG.stream().allMatch(id -> StandDefinitions.definition(id).naturallyAssignable()), "Encounter, animal and unfinished Stands stay outside allocation");
        } finally {
            StandService.dismiss(player);
            server.getPlayerList().remove(player);
        }
        test.succeed();
    }
}
