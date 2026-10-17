package com.github.standobyte.jojo.fabric.stand.tracking;
import com.github.standobyte.jojo.fabric.equipment.BladeHatEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
/** Root adapters connect the source tracker to actual modern world mutations. */
public final class TrackingIntegration {
 public static void initialize(){
  TrackingHooks.initialize();
  net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player,level,hand,entity,hit)->entity instanceof LivingEntity living&&TrackingHooks.refuseInteraction(living,player)?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.PASS);
  ItemTrackingService.registerEntityItem(BladeHatEntity.class,e->((BladeHatEntity)e).trackingStack(),(e,s)->((BladeHatEntity)e).trackingStack(s));
  ServerEntityEvents.ENTITY_LOAD.register((entity,level)->TrackingHooks.entityChanged(entity));
  ServerChunkEvents.CHUNK_LOAD.register((level,chunk,isNew)->{for(var block:chunk.getBlockEntities().values())if(block instanceof Container container)TrackingHooks.containerChanged(level,block.getBlockPos(),container);});
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->TrackingHooks.playerJoin(handler.player));
  ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers())TrackingHooks.playerTick(player);});
  EntityTrackingEvents.START_TRACKING.register((entity,player)->{if(entity instanceof LivingEntity living)StandEffectStore.syncTo(living,player);});
  ServerLifecycleEvents.SERVER_STOPPED.register(server->TrackingHooks.clearRuntime());
 }
 private TrackingIntegration(){}
}
