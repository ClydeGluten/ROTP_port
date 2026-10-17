package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class StandCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                literal("rotp")
                        .then(literal("reload").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(context->{
                            StandConfig.reload(context.getSource().getServer());
                            context.getSource().sendSuccess(()->Component.literal("Stand configuration reloaded."),true);return 1;
                        }))
                        .then(literal("grant").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(argument("players", EntityArgument.players())
                                        .then(literal("star_platinum").executes(context -> {
                                            var players = EntityArgument.getPlayers(context, "players");
                                            players.forEach(StandService::grant);
                                            context.getSource().sendSuccess(() -> Component.literal("Granted Star Platinum to " + players.size() + " player(s)."), true);
                                            return players.size();
                                        }))
                                        .then(literal("the_world").executes(context -> {
                                            var players = EntityArgument.getPlayers(context, "players");
                                            players.forEach(player -> StandService.grant(player, com.github.standobyte.jojo.core.StandProgress.THE_WORLD));
                                            context.getSource().sendSuccess(() -> Component.literal("Granted The World to " + players.size() + " player(s)."), true);
                                            return players.size();
                                        }))))
                        .then(literal("clear").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(argument("players", EntityArgument.players()).executes(context -> {
                                    var players = EntityArgument.getPlayers(context, "players");
                                    players.forEach(StandService::clear);
                                    return players.size();
                                })))
                        .then(literal("summon").executes(context -> StandService.toggle(context.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(literal("punch").executes(context -> StandService.attack(context.getSource().getPlayerOrException(), false) ? 1 : 0))
                        .then(literal("heavy").executes(context -> StandService.attack(context.getSource().getPlayerOrException(), true) ? 1 : 0))
                        .then(literal("uppercut").executes(context -> StandService.uppercut(context.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(literal("finger").executes(context -> StandService.special(context.getSource().getPlayerOrException(), StarPlatinumEntity.PHASE_FINGER) ? 1 : 0))
                        .then(literal("vampire").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(literal("hamon_memory").then(argument("strength", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0, 60))
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                            float strength = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "strength");
                                            VampireHamon.recordHamonTraining(player, strength, "");
                                            if (VampireService.progress(player).vampire())
                                                player.setAttached(VampireHamon.MEMORY, new VampireHamon.Memory(true, strength, ""));
                                            context.getSource().sendSuccess(() -> Component.literal("Set retained Hamon strength to " + strength
                                                    + ". Development adapter only: original Hamon training is a separate power subsystem."), false);
                                            return 1;
                                        })))
                                .then(literal("clear_hamon_memory").executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    VampireHamon.stop(player);
                                    player.setAttached(VampireHamon.MEMORY, VampireHamon.Memory.EMPTY);
                                    context.getSource().sendSuccess(() -> Component.literal("Cleared retained Hamon development state."), false);
                                    return 1;
                                })))
                        .then(literal("progression").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(argument("players", EntityArgument.players())
                                        .then(literal("level").then(argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                                                .executes(context -> {
                                                    var players = EntityArgument.getPlayers(context, "players");
                                                    int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
                                                    players.forEach(player -> ProgressionService.setLevel(player, level));
                                                    context.getSource().sendSuccess(() -> Component.literal("Set Resolve level to " + level + " for " + players.size() + " player(s)."), true);
                                                    return players.size();
                                                })))
                                        .then(literal("training").then(argument("points", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0, 175))
                                                .executes(context -> {
                                                    var players = EntityArgument.getPlayers(context, "players");
                                                    float points = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "points");
                                                    players.forEach(player -> ProgressionService.setTraining(player, points));
                                                    context.getSource().sendSuccess(() -> Component.literal("Set Time Stop training to " + points + " for " + players.size() + " player(s)."), true);
                                                    return players.size();
                                                })))
                                        .then(literal("reset").executes(context -> {
                                            var players = EntityArgument.getPlayers(context, "players");
                                            players.forEach(ProgressionService::reset);
                                            context.getSource().sendSuccess(() -> Component.literal("Reset Stand progression for " + players.size() + " player(s)."), true);
                                            return players.size();
                                        }))))
                        .then(literal("status").executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS);
                            context.getSource().sendSuccess(() -> Component.literal("Stand: " + progress.standId()
                                    + "; stamina: " + progress.stamina() + "/" + progress.maxStamina()
                                    + "; summoned: " + (StandService.current(player) != null)
                                    + "; Resolve level: " + ProgressionService.state(player).level()
                                    + "; Resolve: " + ProgressionService.state(player).resolve() + "/" + ProgressionService.state(player).maxResolve()
                                    + "; Time Stop training: " + ProgressionService.state(player).trainingPoints()
                                    + "; active training cap: " + (ProgressionService.timeStopMaximum(player) - 5)
                                    + "; Time Stop cap: " + (ProgressionService.timeStopMaximum(player) / 20F) + " seconds"), false);
                            return 1;
                        }))));
    }
    private StandCommands() { }
}
