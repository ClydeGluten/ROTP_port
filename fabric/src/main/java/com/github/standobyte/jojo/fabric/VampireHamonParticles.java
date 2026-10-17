package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Original TrHamonParticlesPacket emitter branch, used by accepted Hamon hits. */
public final class VampireHamonParticles {
    public static final SimpleParticleType SPARK = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            RipplesFabric.id("hamon_spark"), FabricParticleTypes.simple(false));
    public record Emitter(int entityId, float intensity) implements CustomPacketPayload {
        public static final Type<Emitter> TYPE = new Type<>(RipplesFabric.id("hamon_particles"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Emitter> CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeInt(value.entityId); buffer.writeFloat(value.intensity); },
                buffer -> new Emitter(buffer.readInt(), buffer.readFloat()));
        @Override public Type<Emitter> type() { return TYPE; }
    }
    /** Original SHORT_SPARK packet branch: contact position, count and short-sound volume. */
    public record ShortSpark(int entityId, Vec3 position, int count, float volume) implements CustomPacketPayload {
        public ShortSpark {
            if(count<0 || count>4096 || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                    || !Double.isFinite(position.z) || !Float.isFinite(volume))throw new IllegalArgumentException("Invalid Hamon short spark");
        }
        public static final Type<ShortSpark> TYPE = new Type<>(RipplesFabric.id("hamon_short_spark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShortSpark> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.entityId);buffer.writeDouble(value.position.x);
                    buffer.writeDouble(value.position.y);buffer.writeDouble(value.position.z);
                    buffer.writeVarInt(value.count);buffer.writeFloat(value.volume);
                }, buffer -> new ShortSpark(buffer.readVarInt(),
                        new Vec3(buffer.readDouble(),buffer.readDouble(),buffer.readDouble()),buffer.readVarInt(),buffer.readFloat()));
        @Override public Type<ShortSpark> type() { return TYPE; }
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(Emitter.TYPE, Emitter.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShortSpark.TYPE, ShortSpark.CODEC);
    }
    public static void shortSpark(net.minecraft.world.entity.Entity target,Vec3 position,int count,float volume) {
        var packet = new ShortSpark(target.getId(),position,count,volume);
        var viewers=new java.util.HashSet<>(PlayerLookup.tracking(target));
        if(target instanceof ServerPlayer player)viewers.add(player);
        for(var viewer:viewers)ServerPlayNetworking.send(viewer,packet);
    }
    public static void emit(net.minecraft.world.entity.Entity target, float intensity) {
        if (!(intensity > 0)) return;
        var packet = new Emitter(target.getId(), Math.min(intensity, 4));
        for (var viewer : PlayerLookup.tracking(target)) ServerPlayNetworking.send(viewer, packet);
        if (target instanceof ServerPlayer player) ServerPlayNetworking.send(player, packet);
    }
    private VampireHamonParticles() { }
}
