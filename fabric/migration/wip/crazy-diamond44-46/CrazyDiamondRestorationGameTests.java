package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.stand.crazydiamond.CrazyDiamondRestorationActions;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.RestorationSelection;
import com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedFireBlock;
import com.github.standobyte.jojo.fabric.stand.restoration.BlockHistory;
import java.util.HashSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Run after plans 45/46 integrate the actual CD factory and source action presentation. */
public final class CrazyDiamondRestorationGameTests {
    static ServerPlayer player(GameTestHelper test) {
        var level = test.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "cd-restoration"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.snapTo(test.absoluteVec(new Vec3(2.5, 2, 1.5)), 0, -90);
        StandService.grant(player, CrazyDiamondRestorationActions.STAND_ID);
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        player.setAttached(StandAttachments.PROGRESS, progress.withStamina(progress.maxStamina()));
        return player;
    }
    static void close(ServerPlayer player) {
        StandActionRegistry.cancel(player); StandService.dismiss(player); player.level().getServer().getPlayerList().remove(player);
    }
    static boolean press(ServerPlayer player, net.minecraft.resources.Identifier action) {
        return StandActionRegistry.dispatch(player, action, StandActionRegistry.Phase.PRESS, false, StandActionRegistry.Extra.EMPTY);
    }
    static void ticks(ServerPlayer player, net.minecraft.resources.Identifier action, int count) {
        for (int i = 0; i < count; i++) {
            StandActionRegistry.dispatch(player, action, StandActionRegistry.Phase.KEEP, false, StandActionRegistry.Extra.EMPTY);
            StandActionRegistry.tick(player);
        }
    }
    static void stop(ServerPlayer player, net.minecraft.resources.Identifier action) {
        StandActionRegistry.dispatch(player, action, StandActionRegistry.Phase.RELEASE, false, StandActionRegistry.Extra.EMPTY);
    }
    static void aim(ServerPlayer player, Vec3 target) {
        var direction = target.subtract(player.getEyePosition());
        player.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180 / Math.PI));
        player.setXRot((float) (-Math.atan2(direction.y, direction.horizontalDistance()) * 180 / Math.PI));
    }

    @GameTest public void actualRepairUnlocksPreviousStateAndReversesRecipeWithoutDuplicatingXp(GameTestHelper test) {
        var player = player(test);
        try {
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setDamageValue(100); sword.set(DataComponents.REPAIR_COST, 15);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
            var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 1);
            sword.enchant(enchantments.getOrThrow(Enchantments.VANISHING_CURSE), 1);
            player.setItemInHand(InteractionHand.OFF_HAND, sword);
            StandActionTraining.set(player, CrazyDiamondRestorationActions.REPAIR, .9999F);
            test.assertFalse(StandActionTraining.unlocked(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Previous State starts training-locked");
            float stamina = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(press(player, CrazyDiamondRestorationActions.REPAIR), "Original offhand action auto-summons actual CD");
            ticks(player, CrazyDiamondRestorationActions.REPAIR, 2);
            test.assertTrue(!sword.isDamaged() && !sword.isEnchanted() && sword.getOrDefault(DataComponents.REPAIR_COST, -1) == 0,
                    "Repair removes damage, enchantments and repair cost through held-action ticks");
            test.assertTrue(sword.get(DataComponents.CUSTOM_NAME).getString().equals("Keep this name"), "Unrelated item components survive");
            int xp = player.level().getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate(3)).stream().mapToInt(ExperienceOrb::getValue).sum();
            test.assertTrue(xp == 1, "Sharpness I returns one source XP; curse returns none and second tick does not duplicate XP");
            test.assertTrue(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() < stamina,
                    "Registry charges the original fractional ticking cost");
            test.assertTrue(StandActionTraining.unlocked(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Max repair training unlocks shifted action");
            stop(player, CrazyDiamondRestorationActions.REPAIR);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_DOOR, 3));
            test.assertTrue(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Actual crafting recipe is reversible");
            ticks(player, CrazyDiamondRestorationActions.PREVIOUS_STATE, 8);
            test.assertTrue(player.getOffhandItem().isEmpty() && player.getInventory().countItem(Items.OAK_PLANKS) == 6,
                    "Three recipe outputs become six actual inventory ingredients once");
            stop(player, CrazyDiamondRestorationActions.PREVIOUS_STATE);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT));
            test.assertFalse(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Any blasting output remains banned");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FILLED_MAP));
            test.assertFalse(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Source-inactive filled-map reversal stays unavailable");
        } finally { close(player); }
        test.succeed();
    }

    @GameTest public void previousStateHonorsResolveAndRewindsActualTntAndMinecart(GameTestHelper test) {
        var player = player(test);
        try {
            StandActionTraining.set(player, CrazyDiamondRestorationActions.REPAIR, 1);
            CrazyDiamondRestorationActions.unlockPreviousState(player);
            ProgressionService.setLevel(player, 2);
            var pos = test.absoluteVec(new Vec3(2.5, 2, 4.5));
            var tnt = EntityTypes.TNT.create(player.level(), EntitySpawnReason.COMMAND);
            tnt.setPos(pos); tnt.setFuse(79); player.level().addFreshEntity(tnt);
            aim(player, tnt.getBoundingBox().getCenter());
            test.assertFalse(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Entity reversal requires resolve 3");
            ProgressionService.setLevel(player, 3);
            test.assertTrue(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Primed TNT bypasses living-target restriction");
            ticks(player, CrazyDiamondRestorationActions.PREVIOUS_STATE, 1);
            test.assertTrue(tnt.getFuse() == 81, "First perform tick rewinds fuse by two");
            ticks(player, CrazyDiamondRestorationActions.PREVIOUS_STATE, 1);
            test.assertTrue(tnt.isRemoved() && player.level().getBlockState(BlockPos.containing(pos)).is(Blocks.TNT), "Subsequent ready fuse becomes a real TNT block");
            stop(player, CrazyDiamondRestorationActions.PREVIOUS_STATE);
            player.level().removeBlock(BlockPos.containing(pos), false);
            var minecart = EntityTypes.TNT_MINECART.create(player.level(), EntitySpawnReason.COMMAND);
            minecart.setPos(pos); player.level().addFreshEntity(minecart); minecart.primeFuse(null);
            aim(player, minecart.getBoundingBox().getCenter());
            test.assertTrue(press(player, CrazyDiamondRestorationActions.PREVIOUS_STATE), "Primed TNT minecart can be targeted");
            ticks(player, CrazyDiamondRestorationActions.PREVIOUS_STATE, 1);
            test.assertTrue(minecart.getFuse() == -1 && !minecart.isRemoved(), "Restoration disarms the actual minecart without replacing it");
            stop(player, CrazyDiamondRestorationActions.PREVIOUS_STATE);
            minecart.discard();
        } finally { close(player); }
        test.succeed();
    }

    @GameTest public void terrainActionUsesPlayerExplosionAndMagiciansRedFireHistory(GameTestHelper test) {
        var player = player(test);
        try {
            var level = player.level();
            ProgressionService.setLevel(player, 2);
            var broken = test.absolutePos(new BlockPos(2, 2, 4));
            var exploded = test.absolutePos(new BlockPos(4, 2, 3));
            var burned = test.absolutePos(new BlockPos(3, 2, 5));
            var fire = burned.east();
            level.setBlockAndUpdate(broken, Blocks.STONE.defaultBlockState());
            test.assertTrue(player.gameMode.destroyBlock(broken), "Player game mode breaks the real source block");
            level.setBlockAndUpdate(exploded, Blocks.GLASS.defaultBlockState());
            var blastCenter = Vec3.atCenterOf(exploded);
            level.explode(null, blastCenter.x, blastCenter.y, blastCenter.z, .8F, Level.ExplosionInteraction.TNT);
            level.setBlockAndUpdate(fire.below(), Blocks.NETHERRACK.defaultBlockState());
            level.setBlockAndUpdate(burned, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(fire, MagiciansRedFireBlock.BLOCK.getStateForPlacement(level, fire));
            var random = RandomSource.create(17);
            for (int i = 0; i < 1000 && level.getBlockState(burned).is(Blocks.OAK_PLANKS); i++)
                level.getBlockState(fire).tick(level, fire, random);
            for (var pos : java.util.List.of(broken, exploded, burned))
                test.assertTrue(BlockHistory.at(level, pos).wasBlockBroken(pos), "Actual break/explosion/MR fire capture is wired at " + pos);
            level.removeBlock(fire, false);
            aim(player, test.absoluteVec(new Vec3(3, 2.5, 4.5)));
            float before = player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(press(player, CrazyDiamondRestorationActions.TERRAIN), "Terrain action sees actual captured source blocks");
            var stand = StandService.currentManifestation(player);
            var restored = new HashSet<BlockPos>();
            for (int i = 0; i < 20 && StandActionRegistry.held(player).isPresent(); i++) {
                ticks(player, CrazyDiamondRestorationActions.TERRAIN, 1);
                restored.addAll(RestorationSelection.state(stand.entity()).restored());
            }
            test.assertTrue(level.getBlockState(broken).is(Blocks.STONE) && level.getBlockState(exploded).is(Blocks.GLASS)
                    && level.getBlockState(burned).is(Blocks.OAK_PLANKS), "Actual server ticks restore all three capture routes");
            test.assertTrue(restored.containsAll(java.util.List.of(broken, exploded, burned)), "Tracked completion state reports actual restored source positions");
            float spent = before - player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(Math.abs(spent - restored.size() * 2) < .01F, "Source stamina cost follows completed placements only");
            test.assertTrue(StandActionRegistry.held(player).filter(CrazyDiamondRestorationActions.TERRAIN::equals).isPresent(),
                    "An empty selection preserves source continue-hold behavior");
        } finally { close(player); }
        test.succeed();
    }
}
