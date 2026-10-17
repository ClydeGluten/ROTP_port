package com.github.standobyte.jojo.fabric;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.saveddata.maps.*;
import java.util.*;
/** Expert cartographer Hamon-map offer and per-player attempt bookkeeping from CustomVillagerTrades. */
public final class HamonTempleTrades {
    public static final Holder<MapDecorationType> ICON=Registry.registerForHolder(BuiltInRegistries.MAP_DECORATION_TYPE,RipplesFabric.id("hamon_temple"),new MapDecorationType(RipplesFabric.id("hamon_temple"),true,true));
    private static final AttachmentType<List<String>> TRIED=AttachmentRegistry.<List<String>>builder().initializer(List::of).persistent(Codec.STRING.listOf()).buildAndRegister(RipplesFabric.id("hamon_map_attempts"));
    private static final AttachmentType<Boolean> GIVEN=AttachmentRegistry.<Boolean>builder().initializer(()->false).persistent(Codec.BOOL).buildAndRegister(RipplesFabric.id("unique_structure_map_given"));
    private static final AttachmentType<Boolean> BOUGHT=AttachmentRegistry.<Boolean>builder().initializer(()->false).persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("hamon_map_bought"));
    private static final Map<UUID,net.minecraft.world.entity.Entity> PENDING_SOUND=new HashMap<>();
    public static void initialize(){
        HamonMapPurchasePayload.initialize();
        UseEntityCallback.EVENT.register((player,level,hand,target,hit)->{
            if(player instanceof ServerPlayer server&&!player.isSpectator()&&hand==InteractionHand.MAIN_HAND&&target instanceof Villager villager)tryOffer(server,villager);
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers()){
            if(player.containerMenu==player.inventoryMenu) {
                var merchant=PENDING_SOUND.remove(player.getUUID());
                if(merchant!=null&&!merchant.isRemoved()&&merchant.level()==player.level()) {
                    var payload=new HamonMapPurchasePayload(merchant.getId(),player.getId());
                    for(var observer:net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(merchant))
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(observer,payload);
                }
            }
        }});
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->PENDING_SOUND.remove(handler.player.getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->PENDING_SOUND.clear());
    }
    public static void onPurchase(net.minecraft.world.entity.player.Player buyer,Merchant merchant,ItemStack result){
        if(!(buyer instanceof ServerPlayer player)||result.isEmpty()||!result.is(Items.FILLED_MAP)
                ||!Component.translatable("filled_map.jojo:hamon_temple").equals(result.get(DataComponents.CUSTOM_NAME))
                ||player.getAttachedOrCreate(BOUGHT))return;
        player.setAttached(BOUGHT,true);
        if(merchant instanceof net.minecraft.world.entity.Entity entity)PENDING_SOUND.put(player.getUUID(),entity);
    }
    public static double chance(ServerPlayer player,Villager villager){
        String biome=villager.getVillagerData().type().unwrapKey().map(k->k.identifier().getPath()).orElse("plains");
        double chance=switch(biome){case "taiga"->1;case "snow","jungle"->0;case "plains","swamp"->.5;default->.125;};
        if(player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand())chance*=.0625;
        if(VampireService.progress(player).vampire())chance*=.125;else if(HamonService.hasHamon(player))chance*=.25;
        return chance;
    }
    private static void tryOffer(ServerPlayer player,Villager villager){
        if(!HamonConfig.server().hamonTempleSpawn()||villager.getAttachedOrCreate(GIVEN)||!villager.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER)||villager.getVillagerData().level()<4)return;
        var tried=villager.getAttachedOrCreate(TRIED);String uuid=player.getUUID().toString();if(tried.contains(uuid))return;
        var next=new ArrayList<>(tried);next.add(uuid);villager.setAttached(TRIED,List.copyOf(next));
        if(player.getRandom().nextDouble()>=chance(player,villager))return;
        var level=player.level();var pos=level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE,RipplesFabric.id("hamon_temple")),villager.blockPosition(),100,true);
        if(pos==null)return;
        ItemStack map=MapItem.create(level,pos.getX(),pos.getZ(),(byte)2,true,true);MapItem.renderBiomePreviewMap(level,map);
        MapItemSavedData.addTargetDecoration(map,pos,"+",ICON);map.set(DataComponents.CUSTOM_NAME,Component.translatable("filled_map.jojo:hamon_temple"));
        map.set(DataComponents.ITEM_MODEL,RipplesFabric.id("hamon_temple_map"));
        CustomData.update(DataComponents.CUSTOM_DATA,map,tag->tag.putString("JojoStructure","hamon_map"));
        villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD,24),Optional.of(new ItemCost(Items.COMPASS)),map,1,23,.2F));villager.setAttached(GIVEN,true);
    }
    private HamonTempleTrades(){}
}
