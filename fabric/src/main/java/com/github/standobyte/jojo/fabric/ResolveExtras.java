package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Original maximum-level counts and visible-only remote/chat boosts. Neither boost changes resolve gain. */
public final class ResolveExtras {
    public record State(Map<String,Integer> extraLevels,float remote,float chat) {
        public State {
            extraLevels=Map.copyOf(extraLevels);
            if(extraLevels.size()>1024||extraLevels.entrySet().stream().anyMatch(e->e.getKey().length()>256||e.getValue()<0)
                    ||!Float.isFinite(remote)||remote<1||remote>5||!Float.isFinite(chat)||chat<1||chat>1.25F)
                throw new IllegalArgumentException("Invalid Resolve extras");
        }
        public State boosts(float remote,float chat){return new State(extraLevels,remote,chat);}
    }
    public static final State EMPTY=new State(Map.of(),1,1);
    private static final Codec<State> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Codec.intRange(0,Integer.MAX_VALUE)).optionalFieldOf("ExtraLevels",Map.of()).forGetter(State::extraLevels),
            Codec.floatRange(1,5).optionalFieldOf("BoostRemoteControl",1F).forGetter(State::remote),
            Codec.floatRange(1,1.25F).optionalFieldOf("BoostChat",1F).forGetter(State::chat)).apply(i,State::new));
    private static final StreamCodec<RegistryFriendlyByteBuf,State> STREAM=StreamCodec.of((b,s)->{
        b.writeVarInt(s.extraLevels.size());s.extraLevels.forEach((id,count)->{b.writeUtf(id,256);b.writeVarInt(count);});
        b.writeFloat(s.remote);b.writeFloat(s.chat);
    },b->{
        int count=b.readVarInt();if(count<0||count>1024)throw new io.netty.handler.codec.DecoderException("Invalid extra Resolve count");
        var levels=new HashMap<String,Integer>();for(int i=0;i<count;i++)levels.put(b.readUtf(256),b.readVarInt());
        return new State(levels,b.readFloat(),b.readFloat());
    });
    public static final AttachmentType<State> DATA=AttachmentRegistry.<State>builder().initializer(()->EMPTY)
            .persistent(CODEC).copyOnDeath().syncWith(STREAM,AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("resolve_extras"));
    public static void initialize(){
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message,player,type)->chat(player,message.signedContent()));
    }
    public static State state(Player player){return player.getAttachedOrCreate(DATA);}
    public static void resetBoosts(Player player){var s=state(player);if(s.remote!=1||s.chat!=1)player.setAttached(DATA,s.boosts(1,1));}
    public static void tick(ServerPlayer player,boolean resetChat){
        var s=state(player);var stand=StandService.currentManifestation(player);
        float remote=stand!=null&&stand.manuallyControlled()?Math.min(5,s.remote+.025F):1;
        var next=s.boosts(remote,resetChat?1:s.chat);if(!next.equals(s))player.setAttached(DATA,next);
    }
    public static void chat(ServerPlayer player,String message){
        if(!player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand())return;
        var resolve=StandAttachments.resolve(player);
        if(resolve.attackBoost()>1||resolve.healthRecord()>-1){var s=state(player);player.setAttached(DATA,s.boosts(s.remote,Math.min(1.25F,s.chat+message.length()*.05F)));}
    }
    public static void increment(ServerPlayer player){
        var id=player.getAttachedOrCreate(StandAttachments.PROGRESS).standId();if(id.isEmpty())return;
        var s=state(player);var levels=new HashMap<>(s.extraLevels);
        levels.compute(id,(key,count)->count==null?1:count==Integer.MAX_VALUE?count:count+1);
        player.setAttached(DATA,new State(levels,s.remote,s.chat));
    }
    public static float visibleBoost(Player player){
        var progress=StandAttachments.resolve(player);var extras=state(player);
        float hp=progress.healthRecord()>=0?Math.min(player.getHealth(),progress.healthRecord()):player.getHealth();
        float health=VampireService.progress(player).vampire()?5:1+(15-Math.clamp(hp,5,15))*.9F;
        return progress.attackBoost()*health*extras.remote*extras.chat;
    }
    private ResolveExtras(){}
}
