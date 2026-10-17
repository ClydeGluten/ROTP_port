package com.github.standobyte.jojo.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.List;

/** Transient observable Hamon state; teaching information is computed by the server. */
public record HamonHudState(boolean meditating,int meditationTicks,boolean breathing,boolean teacherNearby,
        List<String> teacherSkills,List<Integer> learners,boolean introRead) {
    public static final HamonHudState EMPTY=new HamonHudState(false,0,false,false,List.of(),List.of(),false);
    public HamonHudState {teacherSkills=List.copyOf(teacherSkills);learners=List.copyOf(learners);}
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonHudState> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeBoolean(v.meditating());b.writeVarInt(v.meditationTicks());b.writeBoolean(v.breathing());b.writeBoolean(v.teacherNearby());
        b.writeVarInt(v.teacherSkills().size());for(var id:v.teacherSkills())b.writeUtf(id,128);
        b.writeVarInt(v.learners().size());for(int id:v.learners())b.writeVarInt(id);b.writeBoolean(v.introRead());
    },b->{boolean meditate=b.readBoolean();int ticks=b.readVarInt();boolean breath=b.readBoolean(),near=b.readBoolean();
        int count=b.readVarInt();if(count<0 || count>1024)throw new IllegalArgumentException("Invalid teacher skills");
        var skills=new java.util.ArrayList<String>();for(int i=0;i<count;i++)skills.add(b.readUtf(128));
        count=b.readVarInt();if(count<0 || count>1024)throw new IllegalArgumentException("Invalid Hamon learners");
        var learners=new java.util.ArrayList<Integer>();for(int i=0;i<count;i++)learners.add(b.readVarInt());
        return new HamonHudState(meditate,ticks,breath,near,skills,learners,b.readBoolean());});
}
