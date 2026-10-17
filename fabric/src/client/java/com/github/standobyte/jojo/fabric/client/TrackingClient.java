package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
/** Owner-private item markers and public linked-effect snapshots are separate source channels. */
public final class TrackingClient {
 public static void initialize(){
  ClientPlayNetworking.registerGlobalReceiver(ItemTrackingPayload.TYPE,(payload,context)->context.client().execute(()->{if(context.client().player!=null)ItemTrackingPayload.receive(context.client().player,payload);}));
  ClientPlayNetworking.registerGlobalReceiver(ItemTrackingPayload.Effects.TYPE,(payload,context)->context.client().execute(()->{if(context.client().level!=null)StandEffectStore.receive(context.client().level,payload);}));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{ItemTrackingPayload.clearClient();StandEffectStore.clearRuntime();}));
 }
 private TrackingClient(){}
}
