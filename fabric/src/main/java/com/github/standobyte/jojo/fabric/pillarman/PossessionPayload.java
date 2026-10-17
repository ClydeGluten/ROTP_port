package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Server-owned camera/hold snapshot, plus bounded action intents with no client-selected game mode. */
public record PossessionPayload(int playerId, UUID playerUuid, int targetId, UUID targetUuid,
        Identifier dimension, int heldTicks, boolean active, int previousMode, boolean asAlive, Identifier context) implements CustomPacketPayload {
    public PossessionPayload(int playerId, UUID playerUuid, int targetId, UUID targetUuid, Identifier dimension, int heldTicks, boolean active, int previousMode) {
        this(playerId,playerUuid,targetId,targetUuid,dimension,heldTicks,active,previousMode,true,PossessionState.CONTEXT);
    }
    public static final UUID NO_TARGET = new UUID(0, 0);
    public static final Type<PossessionPayload> TYPE = new Type<>(RipplesFabric.id("pillar_possession_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionPayload> CODEC = StreamCodec.of((buf, value) -> {
        buf.writeInt(value.playerId); buf.writeUUID(value.playerUuid); buf.writeInt(value.targetId); buf.writeUUID(value.targetUuid);
        buf.writeIdentifier(value.dimension); buf.writeByte(value.heldTicks); buf.writeBoolean(value.active); buf.writeByte(value.previousMode);buf.writeBoolean(value.asAlive);buf.writeIdentifier(value.context);
    }, buf -> {
        int player = buf.readInt(); UUID uuid = buf.readUUID(); int target = buf.readInt(); UUID targetUuid = buf.readUUID();
        Identifier dimension = buf.readIdentifier(); int ticks = buf.readByte(); boolean active = buf.readBoolean(); int mode = buf.readByte();boolean alive=buf.readBoolean();Identifier context=buf.readIdentifier();
        if (alive!=context.equals(PossessionState.CONTEXT)||!alive&&!context.equals(PossessionState.ANGELO)||player < 0 || target < -1 || ticks < -1 || ticks >= PossessionService.HOLD_TICKS || mode < -1 || mode > 3
                || active && (targetUuid.equals(NO_TARGET) || mode < 0 || ticks != -1)) throw new DecoderException("Invalid possession state");
        return new PossessionPayload(player, uuid, target, targetUuid, dimension, ticks, active, mode, alive, context);
    });
    @Override public Type<PossessionPayload> type() { return TYPE; }
    public enum Action { START, KEEP_HELD, RELEASE, EXIT, RESPAWN, GRUNT }
    public record Intent(Action action, int targetId) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(RipplesFabric.id("pillar_possession_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeByte(value.action.ordinal()); buf.writeInt(value.targetId);
        }, buf -> {
            int action = buf.readUnsignedByte(), target = buf.readInt();
            if (action >= Action.values().length || target < -1) throw new DecoderException("Invalid possession intent");
            return new Intent(Action.values()[action], target);
        });
        @Override public Type<Intent> type() { return TYPE; }
    }
    private static boolean initialized;
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Intent.TYPE, Intent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Intent.TYPE, (payload, context) -> {
            var player = context.player();
            switch (payload.action()) {
                case START -> PossessionService.startHeld(player, player.level().getEntity(payload.targetId()));
                case KEEP_HELD -> PossessionService.keepHeld(player);
                case RELEASE -> PossessionService.stopHeld(player);
                case EXIT -> PossessionService.requestExit(player);
                case RESPAWN -> PossessionService.requestRespawn(player);
                case GRUNT -> PossessionService.requestGrunt(player);
            }
        });
    }
}
