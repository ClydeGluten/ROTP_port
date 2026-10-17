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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Original BloodParticlesPacket keeps one burst packet and excludes the bleeding entity from attachment. */
public final class VampireBloodParticles {
    public static final SimpleParticleType BLOOD = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            RipplesFabric.id("blood"), FabricParticleTypes.simple(false));
    public record Burst(Vec3 source, Vec3 destination, int count, float speed, int entityId) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(RipplesFabric.id("blood_particles"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Burst> CODEC = StreamCodec.of((buffer, value) -> {
            write(buffer, value.source);
            buffer.writeBoolean(value.destination != null);
            if (value.destination != null) write(buffer, value.destination);
            buffer.writeVarInt(value.count); buffer.writeFloat(value.speed); buffer.writeInt(value.entityId);
        }, buffer -> new Burst(read(buffer), buffer.readBoolean() ? read(buffer) : null,
                buffer.readVarInt(), buffer.readFloat(), buffer.readInt()));
        @Override public Type<Burst> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
        private static Vec3 read(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    }
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(Burst.TYPE, Burst.CODEC); }
    public static void spawn(ServerLevel level, Vec3 position, int count, float speed) { spawn(level, position, null, count, speed, null); }
    public static void spawn(ServerLevel level, Vec3 position, int count, float speed, Entity excluded) { spawn(level, position, null, count, speed, excluded); }
    public static void spawn(ServerLevel level, Vec3 position, Vec3 destination, int count, float speed, Entity excluded) {
        var packet = new Burst(position, destination, count, speed, excluded == null ? -1 : excluded.getId());
        for (var player : PlayerLookup.around(level, position, 64)) ServerPlayNetworking.send(player, packet);
    }
    private VampireBloodParticles() { }
}
