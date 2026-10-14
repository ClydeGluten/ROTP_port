package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Transient, owner-only server snapshots. Clients never submit cooldown or availability values. */
public record StandHudState(int finger, int inhale, int timeStop, int blink, int timePunch,
                            int recovery, int charge, int activeStop,
                            int fingerTotal, int inhaleTotal, int timeStopTotal, int blinkTotal, int timePunchTotal) {
    public static final StandHudState EMPTY = new StandHudState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, StandHudState> STREAM_CODEC = StreamCodec.of(
            (b, s) -> { b.writeVarInt(s.finger); b.writeVarInt(s.inhale); b.writeVarInt(s.timeStop);
                b.writeVarInt(s.blink); b.writeVarInt(s.timePunch); b.writeVarInt(s.recovery); b.writeVarInt(s.charge); b.writeVarInt(s.activeStop);
                b.writeVarInt(s.fingerTotal); b.writeVarInt(s.inhaleTotal); b.writeVarInt(s.timeStopTotal); b.writeVarInt(s.blinkTotal); b.writeVarInt(s.timePunchTotal); },
            b -> new StandHudState(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    public static StandHudState capture(ServerPlayer player) {
        if (!player.isAlive() || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()) return EMPTY;
        var stand = StandService.current(player);
        return new StandHudState(AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_FINGER),
                AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_INHALE), TimeStopService.cooldownRemaining(player),
                AbilityCooldowns.remaining(player, 11), AbilityCooldowns.remaining(player, 12),
                stand == null ? 0 : stand.uiRecoveryTicks(), TimeStopService.chargeTicksRemaining(player), TimeStopService.ticksRemaining(player),
                AbilityCooldowns.duration(player, StarPlatinumEntity.PHASE_FINGER),
                AbilityCooldowns.duration(player, StarPlatinumEntity.PHASE_INHALE), TimeStopService.cooldownDuration(player),
                AbilityCooldowns.duration(player, 11), AbilityCooldowns.duration(player, 12));
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                var next = capture(player);
                if (!next.equals(player.getAttachedOrCreate(StandAttachments.HUD))) player.setAttached(StandAttachments.HUD, next);
            }
        });
    }
}
