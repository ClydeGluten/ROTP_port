package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Clients send intent only; ownership, range, damage, stamina and cooldowns are resolved server-side. */
public record StandActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { TOGGLE, LIGHT_PUNCH, HEAVY_PUNCH, START_BARRAGE, START_GUARD, STOP_HELD, KEEP_HELD, UPPERCUT, STAR_FINGER, START_INHALE, START_ZOOM, START_CONTROL, STOP_CONTROL, TIME_STOP, BLINK, TIME_PUNCH, CANCEL_TIME_CHARGE }
    public static final Type<StandActionPayload> TYPE = new Type<>(RipplesFabric.id("stand_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StandActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByte(payload.action.ordinal()),
            buffer -> {
                int action = buffer.readUnsignedByte();
                if (action >= Action.values().length) throw new DecoderException("Unknown stand action: " + action);
                return new StandActionPayload(Action.values()[action]);
            });

    @Override public Type<StandActionPayload> type() { return TYPE; }

    /** Separate channel leaves every legacy enum byte unchanged. No target, damage or training fields exist. */
    public record Registered(Identifier action,StandActionRegistry.Phase phase,boolean shift,StandActionRegistry.Extra extra) implements CustomPacketPayload {
        public static final Type<Registered> TYPE=new Type<>(RipplesFabric.id("registered_stand_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Registered> CODEC=StreamCodec.of((buffer,payload)->{
            buffer.writeByte(2);buffer.writeUtf(payload.action.toString(),256);buffer.writeByte(payload.phase.ordinal());buffer.writeBoolean(payload.shift);
            var extra=payload.extra;int flags=(extra.selection().isPresent()?1:0)|(extra.item().isPresent()?2:0)|(extra.recoveryModifier()?4:0)|(extra.subtype().isPresent()?8:0);
            buffer.writeByte(flags);extra.selection().ifPresent(id->buffer.writeUtf(id.toString(),256));extra.item().ifPresent(buffer::writeUUID);extra.subtype().ifPresent(value->buffer.writeUtf(value,64));
        },buffer->{
            int version=buffer.readUnsignedByte();if(version!=1&&version!=2)throw new DecoderException("Unsupported Stand action payload version");
            Identifier action=Identifier.tryParse(buffer.readUtf(256));int phase=buffer.readUnsignedByte();boolean shift=buffer.readBoolean();int flags=buffer.readUnsignedByte();
            if(action==null||phase>=StandActionRegistry.Phase.values().length||(flags&~(version==1?7:15))!=0)throw new DecoderException("Invalid Stand action intent");
            Identifier selection=(flags&1)!=0?Identifier.tryParse(buffer.readUtf(256)):null;
            if((flags&1)!=0&&selection==null)throw new DecoderException("Invalid Stand action selection");
            var item=(flags&2)!=0?Optional.of(buffer.readUUID()):Optional.<java.util.UUID>empty();
            var subtype=(flags&8)!=0?Optional.of(buffer.readUtf(64)):Optional.<String>empty();
            if(subtype.isPresent()&&(selection==null||!subtype.get().matches("[a-z0-9_./-]{1,64}")))throw new DecoderException("Invalid Stand action subtype");
            return new Registered(action,StandActionRegistry.Phase.values()[phase],shift,new StandActionRegistry.Extra(Optional.ofNullable(selection),item,(flags&4)!=0,subtype));
        });
        public Registered {java.util.Objects.requireNonNull(action);java.util.Objects.requireNonNull(phase);java.util.Objects.requireNonNull(extra);if(action.toString().length()>256)throw new IllegalArgumentException("Action identifier too long");}
        @Override public Type<Registered> type(){return TYPE;}
    }

    public static void initialize() {
        StandActionRegistry.initialize();
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(payload,context)->StandActionRegistry.dispatchLegacy(context.player(),payload.action));
        PayloadTypeRegistry.serverboundPlay().register(Registered.TYPE,Registered.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Registered.TYPE,(payload,context)->StandActionRegistry.dispatch(context.player(),payload.action,payload.phase,payload.shift,payload.extra));
    }
}
