package com.github.standobyte.jojo.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Player-owned cooldowns survive dismiss/re-summon and are cleared with the server session. */
public final class AbilityCooldowns {
    private static final Map<UUID, Map<Integer, Integer>> EXPIRY = new HashMap<>();
    public static int remaining(ServerPlayer player, int ability) {
        return Math.max(0, EXPIRY.getOrDefault(player.getUUID(), Map.of()).getOrDefault(ability, 0)
                - player.level().getServer().getTickCount());
    }
    public static void start(ServerPlayer player, int ability, int ticks) {
        EXPIRY.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(ability,
                player.level().getServer().getTickCount() + ticks);
    }
    public static void clear() { EXPIRY.clear(); }
    private AbilityCooldowns() { }
}
