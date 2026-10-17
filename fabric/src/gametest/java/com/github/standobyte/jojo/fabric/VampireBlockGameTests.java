package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class VampireBlockGameTests {
    private static java.util.UUID protectedPlayer;
    static {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !player.getUUID().equals(protectedPlayer));
    }
    private static final BlockPos CENTER = new BlockPos(2, 3, 4);
    private static ServerPlayer player(GameTestHelper test) {
        var player = VampireGameTests.player(test, "vampire-block", 1.5);
        // Original block-damage calculation includes vanilla's airborne mining penalty.
        player.setOnGround(true);
        VampireService.grant(player, true);
        return player;
    }
    private static void claw(GameTestHelper test, ServerPlayer player) {
        test.assertTrue(VampireService.claw(player), "Claw can begin with empty hand and full vampire state");
        for (int i = 0; i < 12; i++) VampireService.tick(player);
    }
    private static void close(ServerPlayer player) {
        VampireService.clear(player);
        player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void clawBreaksOriginalSevenBlockCrossWithSurvivalDrops(GameTestHelper test) {
        var player = player(test);
        test.setBlock(CENTER, Blocks.STONE);
        // Keep the front neighbor empty until after selection; all six neighbors still receive attempts.
        for (var direction : Direction.values()) if (direction != Direction.NORTH) test.setBlock(CENTER.relative(direction), Blocks.STONE);
        try {
            var start = player.getEyePosition();
            var hit = player.level().clip(new net.minecraft.world.level.ClipContext(start, start.add(player.getLookAngle().scale(10)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            test.assertTrue(hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(test.absolutePos(CENTER)),
                    "Fixture aims at cross center; hit=" + hit.getBlockPos() + ", start=" + start + ", center=" + test.absolutePos(CENTER));
            test.assertTrue(VampireService.target(player, 8) == null, "No unrelated entity intercepts the block attack: " + VampireService.target(player, 8));
            claw(test, player);
            test.assertBlockPresent(Blocks.AIR, CENTER);
            for (var direction : Direction.values()) test.assertBlockPresent(Blocks.AIR, CENTER.relative(direction));
            test.assertTrue(!player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(test.absolutePos(CENTER)).inflate(2)).isEmpty(), "Survival claw preserves original block drops");
            test.succeed();
        } finally { close(player); }
    }
    @GameTest(environment = "jojo:vampire_night", setupTicks = 5) public void clawHonorsRuleAdventureAndBlockProtectionCallbacks(GameTestHelper test) {
        var player = player(test);
        var rules = player.level().getGameRules();
        boolean original = rules.get(JojoGameRules.BREAK_BLOCKS);
        test.setBlock(CENTER, Blocks.STONE);
        try {
            rules.set(JojoGameRules.BREAK_BLOCKS, false, player.level().getServer());
            claw(test, player);
            test.assertBlockPresent(Blocks.STONE, CENTER);
            rules.set(JojoGameRules.BREAK_BLOCKS, true, player.level().getServer());
            player.setGameMode(GameType.ADVENTURE);
            claw(test, player);
            test.assertBlockPresent(Blocks.STONE, CENTER);
            player.setGameMode(GameType.SURVIVAL);
            protectedPlayer = player.getUUID();
            claw(test, player);
            test.assertBlockPresent(Blocks.STONE, CENTER);
            protectedPlayer = null;
            test.setBlock(CENTER, Blocks.BEDROCK);
            claw(test, player);
            test.assertBlockPresent(Blocks.BEDROCK, CENTER);
            test.succeed();
        } finally {
            protectedPlayer = null;
            rules.set(JojoGameRules.BREAK_BLOCKS, original, player.level().getServer());
            close(player);
        }
    }
}
