package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ResolveProgress;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import java.nio.file.*;
import java.util.*;

/** Original common Stand settings. Logical server owns values; joining clients receive the same settings. */
public final class StandConfig {
    public record Data(boolean keepStandOnDeath, boolean skipStandProgression, boolean standStamina,
            boolean soulAscension, boolean endermenBeyondTimeSpace, boolean saveDestroyedBlocks,
            List<Float> resolveLvlPoints, int timeStopChunkRange, float timeStopDamageMultiplier,
            float standPowerMultiplier, float standResistanceMultiplier) {
        public Data {
            resolveLvlPoints=List.copyOf(resolveLvlPoints);
            if(resolveLvlPoints.isEmpty()||resolveLvlPoints.size()>1024||resolveLvlPoints.stream().anyMatch(v->!Float.isFinite(v)||v<=0)
                    ||timeStopChunkRange<0||!range(timeStopDamageMultiplier,0,1)||!range(standPowerMultiplier,0,128)
                    ||!range(standResistanceMultiplier,1,128))throw new IllegalArgumentException("Invalid Stand configuration");
        }
    }
    public static final Data DEFAULTS=new Data(true,false,true,true,true,false,List.of(2500F,10000F,25000F,50000F,32500F),12,1,1,1);
    private static Data server=DEFAULTS,client=DEFAULTS;
    private static final Path PATH=FabricLoader.getInstance().getConfigDir().resolve("jojo/stand.json");
    public static Data server(){return server;}
    public static Data current(boolean clientSide){return clientSide?client:server;}
    public static boolean staminaFree(Player player){return player.isCreative()||!current(player.level().isClientSide()).standStamina();}
    public static boolean skipsTraining(Player player){return player.isCreative()||current(player.level().isClientSide()).skipStandProgression();}
    public static void resetClient(){client=DEFAULTS;ResolveProgress.configureThresholds(server.resolveLvlPoints());}
    public static void apply(Data data){server=data;ResolveProgress.configureThresholds(data.resolveLvlPoints());com.github.standobyte.jojo.fabric.stand.restoration.BlockHistorySavedData.setSaveDestroyedBlocks(data.saveDestroyedBlocks());}
    public static void receive(Data data){client=data;ResolveProgress.configureThresholds(data.resolveLvlPoints());}
    public static void initialize(){
        load();
        PayloadTypeRegistry.clientboundConfiguration().register(StandConfigPayload.TYPE,StandConfigPayload.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler,minecraft)->ServerConfigurationNetworking.send(handler,new StandConfigPayload(server)));
        PayloadTypeRegistry.clientboundPlay().register(StandConfigPayload.TYPE,StandConfigPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,minecraft)->ServerPlayNetworking.send(handler.player,new StandConfigPayload(server)));
    }
    public static void load(){
        var gson=new GsonBuilder().setPrettyPrinting().create();
        try {
            if(!Files.exists(PATH)){Files.createDirectories(PATH.getParent());Files.writeString(PATH,gson.toJson(DEFAULTS)+"\n");server=DEFAULTS;}
            else {
                var j=JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
                List<Float> thresholds=DEFAULTS.resolveLvlPoints();
                if(j.has("resolveLvlPoints"))try {
                    var values=new ArrayList<Float>();for(var value:j.getAsJsonArray("resolveLvlPoints")){float n=value.getAsFloat();if(!Float.isFinite(n)||n<=0)throw new IllegalArgumentException();values.add(n);}
                    if(!values.isEmpty()&&values.size()<=1024)thresholds=values;
                }catch(RuntimeException invalid){RipplesFabric.LOGGER.warn("Invalid resolveLvlPoints; using original defaults");}
                server=new Data(bool(j,"keepStandOnDeath",true),bool(j,"skipStandProgression",false),bool(j,"standStamina",true),
                        bool(j,"soulAscension",true),bool(j,"endermenBeyondTimeSpace",true),bool(j,"saveDestroyedBlocks",false),thresholds,
                        integer(j,"timeStopChunkRange",12),number(j,"timeStopDamageMultiplier",1,0,1),number(j,"standPowerMultiplier",1,0,128),number(j,"standResistanceMultiplier",1,1,128));
            }
        }catch(Exception invalid){server=DEFAULTS;RipplesFabric.LOGGER.warn("Could not load Stand configuration; using original defaults: {}",PATH,invalid);}
        ResolveProgress.configureThresholds(server.resolveLvlPoints());
        com.github.standobyte.jojo.fabric.stand.restoration.BlockHistorySavedData.setSaveDestroyedBlocks(server.saveDestroyedBlocks());
    }
    /** Called by operator reload; existing earned records and training survive a changed threshold. */
    public static void reload(MinecraftServer minecraft){
        load();
        for(var player:minecraft.getPlayerList().getPlayers()){
            ServerPlayNetworking.send(player,new StandConfigPayload(server));
            ProgressionService.normalize(player);
        }
    }
    private static boolean bool(JsonObject j,String key,boolean fallback){try{return j.has(key)&&j.getAsJsonPrimitive(key).isBoolean()?j.get(key).getAsBoolean():fallback;}catch(RuntimeException ignored){return fallback;}}
    private static int integer(JsonObject j,String key,int fallback){try{long n=j.get(key).getAsLong();return n>=0&&n<=Integer.MAX_VALUE?(int)n:fallback;}catch(RuntimeException ignored){return fallback;}}
    private static float number(JsonObject j,String key,float fallback,float min,float max){try{float n=j.get(key).getAsFloat();return range(n,min,max)?n:fallback;}catch(RuntimeException ignored){return fallback;}}
    private static boolean range(float n,float min,float max){return Float.isFinite(n)&&n>=min&&n<=max;}
    private StandConfig(){}
}
