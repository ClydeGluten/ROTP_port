package com.github.standobyte.jojo.fabric;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
public record HamonActionState(String held,int heldTicks,String continuous,int continuousTicks,float spentEnergy,
                               boolean protection,boolean wallClimbing,float wallYaw,Map<String,Integer> cooldowns,List<Integer> detected,String lastUsed) {
    public static final HamonActionState EMPTY=new HamonActionState("",0,"",0,0,false,false,0,Map.of(),List.of(),"");
    public HamonActionState {cooldowns=Map.copyOf(cooldowns);detected=List.copyOf(detected);}
    public int cooldown(String id){return cooldowns.getOrDefault(id,0);}
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonActionState> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeUtf(v.held,96);b.writeVarInt(v.heldTicks);b.writeUtf(v.continuous,96);b.writeVarInt(v.continuousTicks);b.writeFloat(v.spentEnergy);
        b.writeBoolean(v.protection);b.writeBoolean(v.wallClimbing);b.writeFloat(v.wallYaw);b.writeVarInt(v.cooldowns.size());
        v.cooldowns.forEach((k,n)->{b.writeUtf(k,96);b.writeVarInt(n);});b.writeVarInt(v.detected.size());v.detected.forEach(b::writeVarInt);b.writeUtf(v.lastUsed,96);
    },b->{String h=b.readUtf(96);int ht=b.readVarInt();String c=b.readUtf(96);int ct=b.readVarInt();float e=b.readFloat();boolean p=b.readBoolean(),w=b.readBoolean();float y=b.readFloat();
        int count=Math.clamp(b.readVarInt(),0,64);Map<String,Integer> cd=new HashMap<>();for(int i=0;i<count;i++)cd.put(b.readUtf(96),b.readVarInt());
        int dc=Math.clamp(b.readVarInt(),0,4096);List<Integer>d=new ArrayList<>();for(int i=0;i<dc;i++)d.add(b.readVarInt());return new HamonActionState(h,ht,c,ct,e,p,w,y,cd,d,b.readUtf(96));
    });
}
