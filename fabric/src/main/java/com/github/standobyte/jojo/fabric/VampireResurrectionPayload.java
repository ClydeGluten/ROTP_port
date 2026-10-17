package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Original TrResetDeathTimePacket, adapted to typed Fabric play payloads. */
public record VampireResurrectionPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VampireResurrectionPayload> TYPE = new Type<>(RipplesFabric.id("vampire_resurrection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireResurrectionPayload> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.entityId), buffer -> new VampireResurrectionPayload(buffer.readVarInt()));
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
    public static void send(ServerPlayer player) {
        var packet = new VampireResurrectionPayload(player.getId());
        ServerPlayNetworking.send(player, packet);
        for (var observer : PlayerLookup.tracking(player)) if (observer != player) ServerPlayNetworking.send(observer, packet);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
