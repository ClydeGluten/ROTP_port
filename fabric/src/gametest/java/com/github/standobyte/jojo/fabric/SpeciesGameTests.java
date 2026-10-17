package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.UUID;

public final class SpeciesGameTests {
    @GameTest
    public void originalCommandsSpeciesClassificationAndMaskRouting(GameTestHelper test) throws Exception {
        var level = test.getLevel(); var server = level.getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "species-contract"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        var commands = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();
        String target = player.getGameProfile().name();
        var smite = EntityTypePredicate.of(level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE), EntityTypeTags.SENSITIVE_TO_SMITE);
        try {
            StandService.grant(player, StandProgress.STAR_PLATINUM);
            commands.execute("jojopower give " + target + " jojo:zombie", source);
            test.assertTrue(ZombieService.hasZombie(player) && player.isInvertedHealAndHarm(), "Original zombie grant reaches undead potion behavior");
            test.assertTrue(smite.matches(player, level, player.position()), "Smite's actual data predicate recognizes the zombie player");
            test.assertTrue(!player.canBeAffected(new MobEffectInstance(MobEffects.POISON, 100))
                    && !player.canBeAffected(new MobEffectInstance(MobEffects.REGENERATION, 100)), "Vanilla undead effect immunity reaches zombie players");
            test.assertTrue(!HamonService.grant(player) && !VampireService.grant(player, true)
                    && !PillarmanService.grant(player), "Other acquisition routes cannot add a second non-Stand power");
            commands.execute("jojopower clear " + target, source);
            commands.execute("jojopower give " + target + " pillarman", source);
            test.assertTrue(PillarmanService.hasPillarman(player) && !player.isInvertedHealAndHarm()
                    && !smite.matches(player, level, player.position()), "Pillar Man is vampiric but not vanilla undead");
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VampireItems.STONE_MASK));
            test.assertTrue(VampireService.activateMask(player) && PillarmanService.state(player).stage() == 2
                    && !VampireService.progress(player).vampire(), "Shared blood-mask route upgrades Pillar Man without granting vampirism");
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VampireItems.AJA_STONE_MASK));
            test.assertTrue(VampireService.activateMask(player) && PillarmanService.state(player).stage() == 4
                    && PillarmanService.state(player).mode() != PillarmanProgress.Mode.NONE && player.isAlive(), "Aja upgrade precedes non-Pillar lethal handling");
            commands.execute("pillarman set stage " + target + " 1", source);
            commands.execute("pillarman set mode " + target + " wind", source);
            test.assertValueEqual(PillarmanService.state(player).mode(), PillarmanProgress.Mode.WIND, "Original mode command works below stage4");
            test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStarPlatinum(), "Non-Stand grant, clear and evolution preserve separate Stand ownership");
        } finally {
            PillarmanService.clear(player); ZombieService.clear(player);
            StandService.dismiss(player); server.getPlayerList().remove(player);
        }
        test.succeed();
    }
}
