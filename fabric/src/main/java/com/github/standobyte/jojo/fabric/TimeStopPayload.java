package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record TimeStopPayload(
        BlockPos origin,
        Identifier dimension,
        int casterId,
        UUID casterUuid,
        State state,
        int ticksRemaining
) implements CustomPacketPayload {
    public enum State {
        START,
        RESUME,
        SYNC
    }

    public static final Type<TimeStopPayload> TYPE = new Type<>(RipplesFabric.id("time_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.origin);
                buffer.writeIdentifier(payload.dimension);
                buffer.writeInt(payload.casterId);
                buffer.writeUUID(payload.casterUuid);
                buffer.writeByte(payload.state.ordinal());
                buffer.writeInt(payload.ticksRemaining);
            },
            buffer -> {
                BlockPos origin = buffer.readBlockPos(); Identifier dimension = buffer.readIdentifier();
                int casterId = buffer.readInt(); UUID caster = buffer.readUUID();
                int state = buffer.readUnsignedByte(), ticks = buffer.readInt();
                if (state >= State.values().length || ticks < 0 || ticks > 100) throw new io.netty.handler.codec.DecoderException("Invalid time stop state");
                return new TimeStopPayload(origin, dimension, casterId, caster, State.values()[state], ticks);
            }
    );

    @Override
    public Type<TimeStopPayload> type() {
        return TYPE;
    }
}
