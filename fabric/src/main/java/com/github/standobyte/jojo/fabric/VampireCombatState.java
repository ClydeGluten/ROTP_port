package com.github.standobyte.jojo.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Server-owned action phases; charging never trusts a client-supplied duration. */
public record VampireCombatState(boolean freeze, int freezeTicks, int eyesChargeTicks, int eyesTicks,
                                 int eyesCooldown, int auraCooldown, int leapCooldown) {
    public static final VampireCombatState EMPTY = new VampireCombatState(false, 0, 0, 0, 0, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireCombatState> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.freeze); buffer.writeVarInt(value.freezeTicks);
                buffer.writeVarInt(value.eyesChargeTicks); buffer.writeVarInt(value.eyesTicks);
                buffer.writeVarInt(value.eyesCooldown); buffer.writeVarInt(value.auraCooldown); buffer.writeVarInt(value.leapCooldown);
            }, buffer -> new VampireCombatState(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
}
