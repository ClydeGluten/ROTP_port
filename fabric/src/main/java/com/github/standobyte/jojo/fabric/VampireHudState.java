package com.github.standobyte.jojo.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record VampireHudState(Held held, int heldTicks, int clawTicks) {
    public enum Held { NONE, DRAIN, GIFT }
    public static final VampireHudState EMPTY = new VampireHudState(Held.NONE, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireHudState> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeEnum(value.held); buffer.writeVarInt(value.heldTicks); buffer.writeVarInt(value.clawTicks); },
            buffer -> new VampireHudState(buffer.readEnum(Held.class), buffer.readVarInt(), buffer.readVarInt()));
}
