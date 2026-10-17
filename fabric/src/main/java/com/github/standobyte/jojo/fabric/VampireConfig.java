package com.github.standobyte.jojo.fabric;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.nio.file.Files;

/** Original common vampire options, loaded once per server and synchronized to joining clients. */
public final class VampireConfig {
    public record Data(boolean keepVampirismOnDeath, float[] maxBloodMultiplier, float[] bloodDrainMultiplier,
            float[] bloodTickDown, float[] bloodHealCost, boolean vampiresAggroMobs, boolean undeadSunDamage,
            int vampirismCuringDuration) {
        public Data {
            validate(maxBloodMultiplier, true); validate(bloodDrainMultiplier, false);
            validate(bloodTickDown, false); validate(bloodHealCost, false);
            if (vampirismCuringDuration < 1) throw new IllegalArgumentException("Curing duration must be positive");
            maxBloodMultiplier = maxBloodMultiplier.clone(); bloodDrainMultiplier = bloodDrainMultiplier.clone();
            bloodTickDown = bloodTickDown.clone(); bloodHealCost = bloodHealCost.clone();
        }
        private static void validate(float[] values, boolean positive) {
            if (values == null || values.length != 4) throw new IllegalArgumentException("Difficulty arrays require four values");
            for (float value : values) if (!Float.isFinite(value) || (positive ? value <= 0 : value < 0))
                throw new IllegalArgumentException("Invalid vampire configuration value");
        }
    }
    private static final Data DEFAULTS = new Data(true, new float[] {1,1,1,1}, new float[] {0,1,1.75F,2.5F},
            new float[] {.13889F,.00278F,.00278F,0}, new float[] {10,4,2,1}, false, false, 48000);
    private static Data server = DEFAULTS, client = DEFAULTS;
    public static Data server() { return server; }
    public static Data current(boolean clientSide) { return clientSide ? client : server; }
    public static void receive(Data data) { client = data; }
    public static void initialize() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("jojo/vampirism.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) server = gson.fromJson(Files.readString(path), Data.class);
            else { Files.createDirectories(path.getParent()); Files.writeString(path, gson.toJson(DEFAULTS) + "\n"); }
            if (server == null) throw new IllegalArgumentException("Empty vampire configuration");
        } catch (Exception error) { throw new IllegalStateException("Invalid vampire configuration: " + path, error); }
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> ServerPlayNetworking.send(handler.player, new Sync(server)));
    }
    public record Sync(Data data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(RipplesFabric.id("vampire_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, packet) -> {
            var d = packet.data; buf.writeBoolean(d.keepVampirismOnDeath());
            for (var values : new float[][] {d.maxBloodMultiplier(),d.bloodDrainMultiplier(),d.bloodTickDown(),d.bloodHealCost()})
                for (float value : values) buf.writeFloat(value);
            buf.writeBoolean(d.vampiresAggroMobs()); buf.writeBoolean(d.undeadSunDamage()); buf.writeVarInt(d.vampirismCuringDuration());
        }, buf -> {
            boolean keep = buf.readBoolean(); var values = new float[4][4];
            for (var row : values) for (int i=0;i<4;i++) row[i]=buf.readFloat();
            return new Sync(new Data(keep,values[0],values[1],values[2],values[3],buf.readBoolean(),buf.readBoolean(),buf.readVarInt()));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private VampireConfig() { }
}
