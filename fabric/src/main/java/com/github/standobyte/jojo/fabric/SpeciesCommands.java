package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import java.util.Collection;
import static net.minecraft.commands.Commands.*;

/** Original /jojopower and /pillarman syntax; admin grants require an empty non-Stand slot. */
public final class SpeciesCommands {
    private static final java.util.List<String> TYPES = java.util.List.of("jojo:hamon", "jojo:vampirism", "jojo:zombie", "jojo:pillarman");
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("non_stand.unknown", id));
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(literal("jojopower").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(literal("give").then(argument("targets", EntityArgument.players())
                            .then(argument("type", StringArgumentType.greedyString()).suggests((context, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
                                    .executes(SpeciesCommands::give))))
                    .then(literal("clear").then(argument("targets", EntityArgument.players()).executes(SpeciesCommands::clear))));
            var modes = argument("targets", EntityArgument.players());
            for (var mode : PillarmanProgress.Mode.values()) modes.then(literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> changePillar(context, mode, 0)));
            var set = literal("set");
            set.then(literal("stage").then(argument("targets", EntityArgument.players())
                    .then(argument("stage", IntegerArgumentType.integer(1, 4)).executes(context -> changePillar(context, null, IntegerArgumentType.getInteger(context, "stage"))))));
            set.then(literal("mode").then(modes));
            dispatcher.register(literal("pillarman").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).then(set));
        });
    }
    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, "type").trim();
        String type = input.contains(":") ? input : "jojo:" + input;
        if (!TYPES.contains(type)) throw UNKNOWN.create(type);
        var players = EntityArgument.getPlayers(context, "targets");
        int count = 0;
        for (var player : players) {
            if (SpeciesHooks.hasNonStand(player)) continue;
            if (switch (type) {
                case "jojo:hamon" -> HamonService.grant(player);
                case "jojo:vampirism" -> VampireService.grant(player, true);
                case "jojo:zombie" -> ZombieService.grant(player);
                case "jojo:pillarman" -> PillarmanService.grant(player);
                default -> false;
            }) count++;
        }
        requireSuccess(players, count, "commands.non_stand.give.failed");
        int changed = count;
        var name = Component.translatable("non_stand.jojo." + type.substring(5));
        context.getSource().sendSuccess(() -> players.size() == 1
                ? Component.translatable("commands.non_stand.give.success.single", name, players.iterator().next().getDisplayName())
                : Component.translatable("commands.non_stand.give.success.multiple", name, changed), true);
        return count;
    }
    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "targets");
        int count = 0; String lastPower = "";
        for (var player : players) {
            if (!SpeciesHooks.hasNonStand(player)) continue;
            if (HamonService.hasHamon(player)) { lastPower = "hamon"; HamonService.clear(player); }
            else if (SpeciesHooks.vampire(player)) { lastPower = "vampirism"; VampireService.clear(player); }
            else if (ZombieService.hasZombie(player)) { lastPower = "zombie"; ZombieService.clear(player); }
            else { lastPower = "pillarman"; PillarmanService.clear(player); }
            count++;
        }
        requireSuccess(players, count, "commands.non_stand.query.failed");
        int changed = count; var name = Component.translatable("non_stand.jojo." + lastPower);
        context.getSource().sendSuccess(() -> players.size() == 1
                ? Component.translatable("commands.non_stand.remove.success.single", name, players.iterator().next().getDisplayName())
                : Component.translatable("commands.non_stand.remove.success.multiple", changed), true);
        return count;
    }
    private static int changePillar(CommandContext<CommandSourceStack> context, PillarmanProgress.Mode mode, int stage) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "targets");
        int count = 0;
        for (var player : players) if (mode == null ? PillarmanService.setStage(player, stage) : PillarmanService.setMode(player, mode)) count++;
        requireSuccess(players, count, "commands.pillarman.failed");
        int changed = count; Object value = mode == null ? stage : mode.name();
        String key = "commands.pillarman." + (mode == null ? "stage" : "mode") + ".success.";
        context.getSource().sendSuccess(() -> players.size() == 1
                ? Component.translatable(key + "single", value, players.iterator().next().getDisplayName())
                : Component.translatable(key + "multiple", value, changed), true);
        return count;
    }
    private static void requireSuccess(Collection<ServerPlayer> players, int count, String key) throws CommandSyntaxException {
        if (count == 0) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(players.size() == 1
                ? Component.translatable(key + ".single", players.iterator().next().getDisplayName())
                : Component.translatable(key + ".multiple", players.size())).create();
    }
    private SpeciesCommands() { }
}
