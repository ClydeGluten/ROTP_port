package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Intent only: no target, blood amount, disguise flag or owner can be supplied by a client. */
public record ZombieActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { CLAW, START_DEVOUR, START_DISGUISE, KEEP_HELD, STOP_HELD, RELEASE_HELD, LEAP }
    public static final Type<ZombieActionPayload> TYPE = new Type<>(RipplesFabric.id("zombie_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ZombieActionPayload> CODEC = StreamCodec.of(
            (b,p) -> b.writeByte(p.action.ordinal()), b -> {
                int index = b.readUnsignedByte();
                if (index >= Action.values().length) throw new DecoderException("Invalid zombie action");
                return new ZombieActionPayload(Action.values()[index]);
            });
    @Override public Type<ZombieActionPayload> type() { return TYPE; }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            var player = context.player();
            switch (packet.action) {
                case CLAW -> ZombieService.claw(player);
                case START_DEVOUR -> ZombieService.startHeld(player, ZombieAttachments.Held.DEVOUR);
                case START_DISGUISE -> ZombieService.startHeld(player, ZombieAttachments.Held.DISGUISE);
                case KEEP_HELD -> ZombieService.refreshHeld(player);
                case STOP_HELD, RELEASE_HELD -> ZombieService.stopHeld(player);
                case LEAP -> ZombieService.leap(player);
            }
        });
    }
}
