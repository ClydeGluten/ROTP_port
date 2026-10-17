package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;

/** GameplayEventHandler.onChatMessage: Joseph predicts a nearby speaker's next line. */
public final class HamonJosephChat {
    private static final ThreadLocal<ServerPlayer> PREDICTING=new ThreadLocal<>();
    public static void initialize(){
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,speaker,type)->{
            if(PREDICTING.get()!=null)return true;
            var stand=StandService.current(speaker);
            boolean standSpeech=stand!=null && stand.manuallyControlled();
            var center=speaker.getBoundingBox().getCenter();
            // Source entitiesAround uses a centered cube and excludes the speaker.
            for(var joseph:speaker.level().getEntitiesOfClass(ServerPlayer.class,new AABB(center,center).inflate(8),
                    p->p!=speaker && HamonService.hasHamon(p) && HamonService.state(p).technique().equals("joseph")
                            && (!standSpeech || canHearStands(p)))){
                if(joseph.getChatVisibility()==ChatVisiblity.HIDDEN || joseph.getRandom().nextFloat()>=.05F)continue;
                String key="jojo.chat.joseph.next_line."+(joseph.getRandom().nextInt(3)+1);
                Component prediction=Component.translatable(key,message.signedContent());
                // Game-generated, unsigned chat retains translation and the original chat visibility rules.
                var generated=PlayerChatMessage.system(prediction.getString()).withUnsignedContent(prediction);
                var bound=ChatType.bind(ChatType.CHAT,joseph);
                PREDICTING.set(joseph);
                try{
                    // Fabric's chat pipeline offers the generated line to other mods' allow/message hooks.
                    joseph.level().getServer().getPlayerList().broadcastChatMessage(generated,joseph,bound);
                }finally{PREDICTING.remove();}
            }
            return true;
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message,speaker,type)->{if(PREDICTING.get()==speaker)ClackersItem.voice(speaker,"joseph_giggle");});
    }
    private static boolean canHearStands(ServerPlayer player){
        return player.isSpectator() || player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()
                || player.getActiveEffects().stream().anyMatch(e->BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).equals(RipplesFabric.id("spirit_vision")));
    }
    private HamonJosephChat(){}
}
