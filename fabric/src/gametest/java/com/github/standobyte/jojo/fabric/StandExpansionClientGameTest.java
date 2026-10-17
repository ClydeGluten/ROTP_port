package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.client.*;
import com.github.standobyte.jojo.fabric.stand.silverchariot.*;
import com.github.standobyte.jojo.fabric.stand.hierophant.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import java.util.UUID;

/** Integrated and dedicated original-action input, independent models, remote hands, and real180-tick late join. */
public final class StandExpansionClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getConnection().waitForChunksRender();exercise(context,world.getServer(),"integrated");
        }
        var properties=new java.util.Properties();properties.setProperty("server-ip","127.0.0.1");properties.setProperty("online-mode","false");properties.setProperty("max-players","2");
        try(var server=context.worldBuilder().createServer(properties)){
            var casterId=server.computeOnServer(minecraft->{
                var level=minecraft.overworld();var id=UUID.randomUUID();
                var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(id,"late-stop"),false);
                var caster=new ServerPlayer(minecraft,level,cookie.gameProfile(),cookie.clientInformation());
                var wire=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(wire);
                minecraft.getPlayerList().placeNewPlayer(wire,caster,cookie);caster.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                caster.setGameMode(GameType.CREATIVE);caster.setNoGravity(true);caster.snapTo(.5,140,.5,0,0);
                VampireService.grant(caster,true);var blood=VampireService.progress(caster);caster.setAttached(VampireAttachments.PROGRESS,blood.withBlood(blood.maxBlood()));
                StandService.grant(caster,StandProgress.THE_WORLD);
                caster.level().getChunk(0,0);
                return id;
            });
            server.waitFor(minecraft->{var caster=minecraft.getPlayerList().getPlayer(casterId);return caster.level().getEntity(casterId)==caster;});
            server.runOnServer(minecraft->{var caster=minecraft.getPlayerList().getPlayer(casterId);if(!StandService.toggle(caster)||!TimeStopService.toggle(caster))throw new AssertionError("Could not prepare late-join vampire stop");});
            server.waitFor(minecraft->{var caster=minecraft.getPlayerList().getPlayer(casterId);
                if(!TimeStopService.isActive(caster)&&!TimeStopService.isCharging(caster))throw new AssertionError("Late stop cancelled: alive="+caster.isAlive()+", spectator="+caster.isSpectator()+", stand="+StandService.current(caster)+", mode="+caster.gameMode()+", pos="+caster.position());
                return TimeStopService.isActive(caster);});
            try(var connection=server.connect()){
                context.waitFor(client->client.player!=null&&TimeStopClient.isFrozen(client.player)&&TimeStopClient.ticksRemaining()>100,100);
                context.takeScreenshot("stands-dedicated-late-join-nine-second-stop");
                server.runOnServer(minecraft->{var caster=minecraft.getPlayerList().getPlayer(casterId);if(TimeStopService.durationTicks(caster)!=180)throw new AssertionError("Wrong vampire duration");StandService.dismiss(caster);minecraft.getPlayerList().remove(caster);});
                context.waitFor(client->!TimeStopClient.isFrozen(client.player));connection.waitForChunksRender();exercise(context,server,"dedicated");
            }
        }
    }
    private static KeyMapping key(ClientGameTestContext context,String id){return context.computeOnClient(client->KeyMapping.get(id));}
    private static void select(ClientGameTestContext context,boolean attack,int slot){
        var focus=key(context,attack?"jojo.key.attack_hotbar":"jojo.key.ability_hotbar");
        context.getInput().holdKey(focus);context.getInput().pressKey(options->options.keyHotbarSlots[slot]);context.getInput().releaseKey(focus);
    }
    private static void click(ClientGameTestContext context,boolean attack){int button=attack?InputConstants.MOUSE_BUTTON_LEFT:InputConstants.MOUSE_BUTTON_RIGHT;context.getInput().holdMouse(button);context.waitTicks(1);context.getInput().releaseMouse(button);}
    private static boolean ready(net.minecraft.client.Minecraft client,net.minecraft.resources.Identifier action){return client.player.getAttachedOrCreate(StandHudState.REGISTERED).actions().stream().anyMatch(v->v.action().equals(action)&&v.available());}
    private static void exercise(ClientGameTestContext context,TestServerContext server,String mode){
        var uuid=context.computeOnClient(client->client.player.getUUID());
        for(String standId:java.util.List.of(StandProgress.SILVER_CHARIOT,StandProgress.HIEROPHANT_GREEN)){
            boolean chariot=standId.equals(StandProgress.SILVER_CHARIOT);
            int victim=server.computeOnServer(minecraft->{
                var player=minecraft.getPlayerList().getPlayer(uuid);StandService.clear(player);player.setGameMode(GameType.CREATIVE);player.setNoGravity(true);
                player.connection.teleport(.5,140,.5,0,15);StandService.grant(player,standId);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=12;z++)player.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,139,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                var cow=EntityTypes.IRON_GOLEM.create(player.level(),EntitySpawnReason.COMMAND);cow.snapTo(.5,140,3.2,180,0);cow.setNoGravity(true);cow.setNoAi(true);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);cow.setHealth(500);player.level().addFreshEntity(cow);return cow.getId();
            });
            context.waitFor(client->client.player.getAttachedOrCreate(StandHudState.REGISTERED).stand().equals(standId)&&client.level.getEntity(victim)!=null);
            context.getInput().pressKey(key(context,"key.jojo.fabric.summon"));
            context.waitFor(client->StandView.ownManifestation(client)!=null&&StandView.ownManifestation(client).definition().id().equals(standId));
            context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_BACK));context.waitTicks(20);
            context.takeScreenshot("stands-"+mode+"-"+(chariot?"silver-chariot-armored":"hierophant-green-model"));
            context.runOnClient(client->client.options.setCameraType(CameraType.FIRST_PERSON));context.getInput().lookAt(0,15);
            select(context,true,chariot?0:1);
            var action=chariot?SilverChariotActions.ATTACK:HierophantGreenActions.SPLASH;
            context.waitFor(client->ready(client,action));click(context,true);
            server.waitFor(minecraft->((LivingEntity)minecraft.getPlayerList().getPlayer(uuid).level().getEntity(victim)).getHealth()<500);
            context.takeScreenshot("stands-"+mode+"-"+(chariot?"silver-chariot-light":"emerald-splash-hud"));
            if(chariot){
                select(context,true,1);context.waitFor(client->ready(client,SilverChariotActions.BARRAGE));
                context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
                context.waitFor(client->StandView.ownManifestation(client) instanceof SilverChariotEntity stand&&stand.attackKind()==SilverChariotEntity.Kind.BARRAGE);
                context.takeScreenshot("stands-"+mode+"-silver-chariot-barrage");context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
                select(context,false,1);context.waitFor(client->ready(client,SilverChariotActions.ARMOR));click(context,false);
                context.waitFor(client->StandView.ownManifestation(client) instanceof SilverChariotEntity stand&&!stand.hasArmor());
                context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_BACK));context.takeScreenshot("stands-"+mode+"-silver-chariot-armor-removed");
                context.runOnClient(client->client.options.setCameraType(CameraType.FIRST_PERSON));
                server.runOnServer(minecraft->minecraft.getPlayerList().getPlayer(uuid).setGameMode(GameType.SURVIVAL));
                select(context,true,2);context.waitFor(client->ready(client,SilverChariotActions.LAUNCH));click(context,true);
                context.waitFor(client->client.player.getAttachedOrCreate(StandHudState.REGISTERED).actions().stream().anyMatch(v->v.action().equals(SilverChariotActions.LAUNCH)&&v.remaining()>0));
                context.takeScreenshot("stands-"+mode+"-silver-chariot-rapier");
            }else{
                server.waitFor(minecraft->((HierophantGreenEntity)StandService.currentManifestation(minecraft.getPlayerList().getPlayer(uuid))).task()==HierophantGreenEntity.Task.NONE);
            }
            context.getInput().holdKey(key(context,"key.jojo.fabric.control"));
            context.waitFor(client->client.getCameraEntity() instanceof StandManifestation stand&&stand.manuallyControlled());
            context.takeScreenshot("stands-"+mode+"-"+(chariot?"silver-chariot":"hierophant-green")+"-remote-hands");
            context.getInput().releaseKey(key(context,"key.jojo.fabric.control"));context.waitFor(client->client.getCameraEntity()==client.player);
            server.runOnServer(minecraft->{var player=minecraft.getPlayerList().getPlayer(uuid);player.level().getEntity(victim).discard();StandService.dismiss(player);});
            context.waitFor(client->StandView.ownManifestation(client)==null);
        }
    }
}
