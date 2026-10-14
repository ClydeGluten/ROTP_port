package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandAllocation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Server-thread allocation, persisted globally on the Overworld. Commands bypass natural allocation. */
public final class StandAssignments {
    public static final List<String> CATALOG = StandAllocation.DEFAULT_CATALOG;
    public record WorldCycle(int generation, List<String> used) {
        public static final WorldCycle INITIAL = new WorldCycle(1, List.of());
        public WorldCycle { generation = Math.max(1, generation); used = List.copyOf(used); }
        StandAllocation.StandCycle core() { return new StandAllocation.StandCycle(generation, used); }
    }
    public static final Codec<WorldCycle> CYCLE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("generation", 1).forGetter(WorldCycle::generation),
            Codec.STRING.listOf().optionalFieldOf("used", List.of()).forGetter(WorldCycle::used)
    ).apply(i, WorldCycle::new));
    public static final AttachmentType<WorldCycle> CYCLE = AttachmentRegistry.<WorldCycle>builder()
            .initializer(() -> WorldCycle.INITIAL).persistent(CYCLE_CODEC).buildAndRegister(RipplesFabric.id("stand_cycle"));
    public static final AttachmentType<Integer> PROVISIONAL = AttachmentRegistry.<Integer>builder()
            .initializer(StandAllocation::randomProvisional).persistent(Codec.INT).copyOnDeath()
            .buildAndRegister(RipplesFabric.id("stand_provisional"));
    // A per-player migration marker never expires. No growing/evicted global UUID history can re-reserve old owners.
    public static final AttachmentType<Boolean> ACCOUNTED = AttachmentRegistry.<Boolean>builder()
            .initializer(() -> false).persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("stand_allocation_accounted"));
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
    }
    public static WorldCycle getCycle(MinecraftServer server) { return server.overworld().getAttachedOrCreate(CYCLE); }
    public static WorldCycle getCycle(ServerPlayer player) { return getCycle(player.level().getServer()); }
    public static void setCycle(MinecraftServer server, WorldCycle cycle) { server.overworld().setAttached(CYCLE, cycle); }
    public static int getProvisional(ServerPlayer player) {
        int value = player.getAttachedOrCreate(PROVISIONAL);
        if (value < 1 || value > CATALOG.size()) { value = StandAllocation.randomProvisional(); player.setAttached(PROVISIONAL, value); }
        return value;
    }
    public static void setProvisional(ServerPlayer player, int value) { player.setAttached(PROVISIONAL, value); }
    /** Called only when the Arrow's awakening has succeeded, before ownership is assigned in the same server tick. */
    public static String allocateStand(ServerPlayer player) {
        var server = player.level().getServer();
        var result = StandAllocation.allocate(getCycle(server).core(), getProvisional(player));
        setCycle(server, new WorldCycle(result.newCycle().generation(), result.newCycle().reserved()));
        player.setAttached(ACCOUNTED, true);
        // The final integer now describes the actual permanent Stand, even if its provisional choice collided.
        player.setAttached(PROVISIONAL, CATALOG.indexOf(result.standId()) + 1);
        return result.standId();
    }
    /** Existing Fabric owners seed once on their first join after this update. New command grants never seed on relogin. */
    public static void seedOwner(ServerPlayer player) {
        if (player.getAttachedOrCreate(ACCOUNTED)) return;
        var power = player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (power.hasStand()) {
            var seeded = StandAllocation.seed(getCycle(player).core(), power.standId());
            setCycle(player.level().getServer(), new WorldCycle(seeded.generation(), seeded.reserved()));
        }
        player.setAttached(ACCOUNTED, true);
    }
    public static void onPlayerJoin(ServerPlayer player) { getProvisional(player); seedOwner(player); }
    private StandAssignments() { }
}
