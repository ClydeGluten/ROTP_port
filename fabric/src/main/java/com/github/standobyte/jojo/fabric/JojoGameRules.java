package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** Original ModGamerules.BREAK_BLOCKS, expressed as the namespaced modern game rule. */
public final class JojoGameRules {
    public static final GameRule<Boolean> BREAK_BLOCKS = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.PLAYER).buildAndRegister(RipplesFabric.id("abilities_break_blocks"));

    public static void initialize() { }
    private JojoGameRules() { }
}
