package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            switch (payload.action) {
                case TOGGLE -> StandService.toggle(context.player());
                case LIGHT_PUNCH -> StandService.attack(context.player(), false);
                case HEAVY_PUNCH -> StandService.attack(context.player(), true);
                case START_BARRAGE -> StandService.startHeld(context.player(), false);
                case START_GUARD -> StandService.startHeld(context.player(), true);
                case STOP_HELD -> StandService.stopHeld(context.player());
                case KEEP_HELD -> StandService.refreshHeld(context.player());
                case UPPERCUT -> StandService.uppercut(context.player());
                case STAR_FINGER -> StandService.special(context.player(), StarPlatinumEntity.PHASE_FINGER);
                case START_INHALE -> StandService.special(context.player(), StarPlatinumEntity.PHASE_INHALE);
                case START_ZOOM -> StandService.special(context.player(), StarPlatinumEntity.PHASE_ZOOM);
                case START_CONTROL -> StandService.startControl(context.player());
                case STOP_CONTROL -> StandService.stopControl(context.player());
                case TIME_STOP -> {
                    StandService.stopControl(context.player());
                    if (!TimeStopService.toggle(context.player()) && (!context.player().getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()
                            || ProgressionService.unlocked(context.player(), context.player().getAttachedOrCreate(StandAttachments.PROGRESS).timeStopUnlock()))) context.player().sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            "jojo.fabric.time_unavailable", TimeStopService.cooldownRemaining(context.player()) / 20F));
                }
                case BLINK -> TheWorldActions.blink(context.player());
                case TIME_PUNCH -> TheWorldActions.timePunch(context.player());
                case CANCEL_TIME_CHARGE -> TimeStopService.cancelCharge(context.player());
            }
        });
    }
}
