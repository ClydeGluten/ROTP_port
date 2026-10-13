package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Directional intent; the server controls speed, position, distance and input expiry. */
public record StandControlPayload(float sideways, float forward, float vertical, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<StandControlPayload> TYPE = new Type<>(RipplesFabric.id("stand_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StandControlPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeFloat(p.sideways); b.writeFloat(p.forward); b.writeFloat(p.vertical); b.writeFloat(p.yaw); b.writeFloat(p.pitch); },
            b -> new StandControlPayload(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
    @Override public Type<StandControlPayload> type() { return TYPE; }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            var stand = StandService.current(context.player());
            if (stand != null && !TimeStopService.isFrozen(context.player()) && !TimeStopService.isCharging(context.player())) stand.control(payload.sideways, payload.forward, payload.vertical, payload.yaw, payload.pitch);
        });
    }
}
