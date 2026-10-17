package com.github.standobyte.jojo.fabric;
import com.github.standobyte.jojo.core.HamonProgress;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import static net.minecraft.commands.Commands.*;
/** Original /hamonstat grammar plus the Fabric draft's explicit administrative grant/clear helpers. */
public final class HamonCommands {
    private static final DynamicCommandExceptionType FAILED=new DynamicCommandExceptionType(player->Component.translatable("commands.hamon.failed.single",player));
    public static void initialize(){CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
        var root=literal("hamonstat").requires(s->s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        for(String operation:new String[]{"set","add"}){
            var op=literal(operation);
            for(String stat:new String[]{"strength","control","breathing"}){
                var level=argument("level",FloatArgumentType.floatArg(0,stat.equals("breathing")?100:60)).executes(c->change(c,operation,stat,true));
                if(!stat.equals("breathing"))level.then(argument("ignoreBreathing",BoolArgumentType.bool()).executes(c->change(c,operation,stat,BoolArgumentType.getBool(c,"ignoreBreathing"))));
                op.then(literal(stat).then(argument("targets",EntityArgument.players()).then(level)));
            }
            root.then(op);
        }
        var query=literal("query");
        for(String stat:new String[]{"strength","control","breathing"})query.then(literal(stat).then(argument("target",EntityArgument.player()).executes(c->{
            var player=EntityArgument.getPlayer(c,"target");if(!HamonService.hasHamon(player))throw FAILED.create(player.getDisplayName());
            var state=HamonService.state(player);float value=stat.equals("breathing")?state.breathingLevel():stat.equals("strength")?state.strengthLevel():state.controlLevel();
            c.getSource().sendSuccess(()->Component.translatable("commands.hamon."+stat+".query.success",player.getDisplayName(),new java.text.DecimalFormat("#.##").format(value)),false);return(int)value;
        })));
        dispatcher.register(root.then(query));
        dispatcher.register(literal("rotp").then(literal("hamon").requires(s->s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("grant").then(argument("players",EntityArgument.players()).executes(c->{int count=0;for(var p:EntityArgument.getPlayers(c,"players"))if(HamonService.grant(p))count++;return count;})))
                .then(literal("clear").then(argument("players",EntityArgument.players()).executes(c->{var players=EntityArgument.getPlayers(c,"players");players.forEach(HamonService::clear);return players.size();})))));
    });}
    private static int change(CommandContext<CommandSourceStack> context,String operation,String stat,boolean ignore) throws CommandSyntaxException {
        var players=EntityArgument.getPlayers(context,"targets");float level=FloatArgumentType.getFloat(context,"level");int count=0;
        for(var player:players){if(!HamonService.hasHamon(player))continue;var old=HamonService.state(player);
            if(stat.equals("breathing"))HamonService.setBreathing(player,(operation.equals("add")?old.breathingLevel():0)+level);
            else{
                boolean strength=stat.equals("strength");int previous=strength?old.strengthPoints():old.controlPoints();
                float target=(operation.equals("add")?HamonProgress.levelFractionFromPoints(previous):0)+level;
                int points=HamonProgress.pointsAtLevelFraction(target);int gap=HamonConfig.server().breathingHamonStatGap();
                if(!ignore&&gap>=0){int limit=Math.min((int)old.breathingLevel()+gap,60);if(HamonProgress.levelFromPoints(points)>limit)points=HamonProgress.pointsAtLevel(limit+1)-1;}
                HamonService.setStats(player,strength?points:old.strengthPoints(),strength?old.controlPoints():points);
                player.setAttached(HamonAttachments.PROGRESS,HamonService.state(player).withTutorial(false));
            }
            count++;
        }
        if(count==0)throw FAILED.create(players.iterator().next().getDisplayName());
        int successes=count;boolean single=players.size()==1;String key="commands.hamon."+stat+"."+(operation.equals("add")?"add.":"")+"success."+(single?"single":"multiple");
        context.getSource().sendSuccess(()->Component.translatable(key,level,single?players.iterator().next().getDisplayName():successes),true);return count;
    }
    private HamonCommands(){}
}
