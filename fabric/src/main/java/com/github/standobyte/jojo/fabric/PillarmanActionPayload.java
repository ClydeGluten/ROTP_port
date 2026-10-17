package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Intent only. The server owns the selected target, stage, resource costs and action clock. */
public record PillarmanActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { HEAVY_PUNCH, START_ABSORPTION, START_STONE_FORM, REGENERATION, START_SENSES,
        START_EVASION, START_AGILITY, KEEP_HELD, STOP_HELD, RELEASE_HELD, LEAP, HORN, RIBS, ERRATIC_BLAZE, START_GIANT_HEAT, START_DETONATION, START_LIGHT_FLASH, START_LIGHT_DECOY, BLADE_SLASH, START_BLADE_DASH, START_BLADE_BARRAGE }
    public static final Type<PillarmanActionPayload> TYPE = new Type<>(RipplesFabric.id("pillarman_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PillarmanActionPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeByte(value.action.ordinal()), buf -> {
                int index = buf.readUnsignedByte();
                if (index >= Action.values().length) throw new DecoderException("Invalid Pillar action");
                return new PillarmanActionPayload(Action.values()[index]);
            });
    @Override public Type<PillarmanActionPayload> type() { return TYPE; }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> PillarmanBaseActions.receive(context.player(), payload));
    }
}
