package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Original direct-position/no-motion-lerp packets plus implied clocks, scoped to a tracked entity incarnation. */
public record StandTimeSkipPayload(Identifier dimension, int entityId, UUID uuid, Vec3 position,
                                   float yaw, float pitch, int ticks) implements CustomPacketPayload {
    public static final Type<StandTimeSkipPayload> TYPE = new Type<>(RipplesFabric.id("time_skip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StandTimeSkipPayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeIdentifier(p.dimension); b.writeInt(p.entityId); b.writeUUID(p.uuid);
                b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);
                b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeVarInt(p.ticks);
            }, b -> new StandTimeSkipPayload(b.readIdentifier(), b.readInt(), b.readUUID(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat(), b.readVarInt()));
    @Override public Type<StandTimeSkipPayload> type() { return TYPE; }
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
    public static void send(ServerPlayer player, StarPlatinumEntity stand, int ticks) {
        sendEntity(player, ticks);
        if (stand != null && !stand.isRemoved()) sendEntity(stand, ticks);
    }
    private static void sendEntity(Entity entity, int ticks) {
        var payload = new StandTimeSkipPayload(entity.level().dimension().identifier(), entity.getId(), entity.getUUID(),
                entity.position(), entity.getYRot(), entity.getXRot(), ticks);
        for (ServerPlayer viewer : PlayerLookup.tracking(entity)) ServerPlayNetworking.send(viewer, payload);
        if (entity instanceof ServerPlayer player) ServerPlayNetworking.send(player, payload);
    }
}
