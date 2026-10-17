package com.github.standobyte.jojo.fabric;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.file.Files;
import java.util.List;

/** Original common Hamon options; the authoritative server copy is sent at login. */
public final class HamonConfig {
    public record Data(boolean keepHamonOnDeath,boolean hamonTempleSpawn,float hamonDamageMultiplier,
            boolean hamonEnergyTicksDown,float hamonPointsMultiplier,float breathingTrainingMultiplier,
            boolean breathingTrainingDeterioration,int breathingHamonStatGap,boolean mixHamonTechniques,
            List<Integer> techniqueSkillsRequirement) {
        public Data {
            if (!finiteRange(hamonDamageMultiplier,128) || !finiteRange(hamonPointsMultiplier,5000)
                    || !finiteRange(breathingTrainingMultiplier,100) || breathingHamonStatGap < -1 || breathingHamonStatGap >100
                    || techniqueSkillsRequirement==null || techniqueSkillsRequirement.stream().anyMatch(i->i==null || i<0))
                throw new IllegalArgumentException("Invalid Hamon configuration");
            techniqueSkillsRequirement=List.copyOf(techniqueSkillsRequirement);
        }
        private static boolean finiteRange(float value,float max) {return Float.isFinite(value) && value>=0 && value<=max;}
    }
    public static final Data DEFAULTS=new Data(true,true,1,true,1,1,true,-1,false,List.of(20,30,40));
    private static Data server=DEFAULTS,client=DEFAULTS;
    public static Data server() {return server;}
    public static Data current(boolean clientSide) {return clientSide?client:server;}
    public static void receive(Data data) {client=data;}
    public static void initialize() {
        var path=FabricLoader.getInstance().getConfigDir().resolve("jojo/hamon.json");
        var gson=new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) server=gson.fromJson(Files.readString(path),Data.class);
            else {Files.createDirectories(path.getParent());Files.writeString(path,gson.toJson(DEFAULTS)+"\n");}
            if (server==null)throw new IllegalArgumentException("Empty Hamon configuration");
        } catch(Exception e) {throw new IllegalStateException("Invalid Hamon configuration: "+path,e);}
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE,Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,minecraft)->ServerPlayNetworking.send(handler.player,new Sync(server)));
    }
    public record Sync(Data data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE=new Type<>(RipplesFabric.id("hamon_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sync> CODEC=StreamCodec.of((b,p)->{
            var d=p.data();b.writeBoolean(d.keepHamonOnDeath());b.writeBoolean(d.hamonTempleSpawn());b.writeFloat(d.hamonDamageMultiplier());
            b.writeBoolean(d.hamonEnergyTicksDown());b.writeFloat(d.hamonPointsMultiplier());b.writeFloat(d.breathingTrainingMultiplier());
            b.writeBoolean(d.breathingTrainingDeterioration());b.writeVarInt(d.breathingHamonStatGap());b.writeBoolean(d.mixHamonTechniques());
            b.writeVarInt(d.techniqueSkillsRequirement().size());for(int n:d.techniqueSkillsRequirement())b.writeVarInt(n);
        },b->{boolean keep=b.readBoolean(),temple=b.readBoolean();float damage=b.readFloat();boolean decay=b.readBoolean();
            float points=b.readFloat(),training=b.readFloat();boolean deterioration=b.readBoolean();int gap=b.readVarInt();boolean mix=b.readBoolean();
            int count=b.readVarInt();if(count<0 || count>1024)throw new IllegalArgumentException("Invalid technique slots");
            var slots=new java.util.ArrayList<Integer>();for(int i=0;i<count;i++)slots.add(b.readVarInt());
            return new Sync(new Data(keep,temple,damage,decay,points,training,deterioration,gap,mix,slots));});
        @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    }
    private HamonConfig() { }
}
