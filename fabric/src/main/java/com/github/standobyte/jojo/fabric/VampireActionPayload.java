package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only action intent crosses the wire. The server resolves targets, costs and eligibility. */
public record VampireActionPayload(Action action) implements CustomPacketPayload {
    public enum Action { CLAW, START_DRAIN, START_GIFT, STOP_HELD, KEEP_HELD, START_FREEZE, START_EYES,
        DARK_AURA, SUMMON_ZOMBIES, LEAP, START_HAMON, RELEASE_HELD }
    public static final Type<VampireActionPayload> TYPE = new Type<>(RipplesFabric.id("vampire_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireActionPayload> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeByte(value.action.ordinal()), buffer -> {
                int ordinal = buffer.readUnsignedByte();
                if (ordinal >= Action.values().length) throw new DecoderException("Invalid vampire action");
                return new VampireActionPayload(Action.values()[ordinal]);
            });
    @Override public Type<VampireActionPayload> type() { return TYPE; }
    /** Original checkRequirements and canBeUsedDuringPlayerAction, shared by server action entrypoints. */
    public static boolean canStart(net.minecraft.server.level.ServerPlayer player, Action action) {
        if (!VampireService.progress(player).vampire() || !player.isAlive() || player.isSpectator() || TimeStopService.isFrozen(player)) return false;
        var basic = player.getAttachedOrCreate(VampireAttachments.HUD);
        var combat = VampireCombatService.state(player);
        if (basic.clawTicks() > 0 || combat.eyesTicks() > 0) return false;
        if (basic.held() == VampireHudState.Held.GIFT || combat.eyesChargeTicks() > 0 || player.getAttachedOrCreate(VampireHamon.CHARGE) > 0) return false;
        if (basic.held() == VampireHudState.Held.DRAIN && action == Action.START_DRAIN || combat.freeze() && action == Action.START_FREEZE) return false;
        return action == Action.START_EYES || action == Action.DARK_AURA || action == Action.START_HAMON || !VampireCombatService.isStunned(player);
    }
    /** Call only after new action validation succeeds: failed input must preserve the current hold. */
    public static void stopAllHeld(net.minecraft.server.level.ServerPlayer player) {
        VampireService.stopHeld(player); VampireCombatService.stopHeld(player); VampireHamon.stop(player);
    }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            switch (payload.action) {
                case CLAW -> VampireService.claw(context.player());
                case START_DRAIN -> VampireService.startHeld(context.player(), VampireHudState.Held.DRAIN);
                case START_GIFT -> VampireService.startHeld(context.player(), VampireHudState.Held.GIFT);
                case RELEASE_HELD -> { VampireCombatService.releaseEyesCharge(context.player()); stopAllHeld(context.player()); }
                case STOP_HELD -> { VampireService.stopHeld(context.player()); VampireCombatService.stopHeld(context.player()); VampireHamon.stop(context.player()); }
                case KEEP_HELD -> { VampireService.refreshHeld(context.player()); VampireCombatService.keepHeld(context.player()); VampireHamon.keep(context.player()); }
                case START_FREEZE -> VampireCombatService.startFreeze(context.player());
                case START_EYES -> VampireCombatService.startEyesCharge(context.player());
                case DARK_AURA -> VampireCombatService.evilAtmosphere(context.player());
                case SUMMON_ZOMBIES -> { if (canStart(context.player(), Action.SUMMON_ZOMBIES) && VampireZombieService.summon(context.player())) stopAllHeld(context.player()); }
                case LEAP -> VampireCombatService.leap(context.player());
                case START_HAMON -> { if (canStart(context.player(), Action.START_HAMON) && VampireHamon.start(context.player())) { VampireService.stopHeld(context.player()); VampireCombatService.stopHeld(context.player()); } }
            }
        });
    }
}
