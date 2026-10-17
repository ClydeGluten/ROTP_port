package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

/** Original ActionCooldownTracker's remaining/total clocks, persisted by action identifier. */
public final class AbilityCooldowns {
    public record Entry(Identifier action, int remaining, int total) {
        public Entry {
            if(action==null||action.toString().length()>256||remaining<0||total<0)throw new IllegalArgumentException("Invalid action cooldown");
        }
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.validate(id->id.toString().length()<=256?com.mojang.serialization.DataResult.success(id):com.mojang.serialization.DataResult.error(()->"Cooldown action identifier too long"))
                        .fieldOf("action").forGetter(Entry::action),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("remaining").forGetter(Entry::remaining),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("total").forGetter(Entry::total)).apply(i, Entry::new));
    }
    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf(0, 512);
    public static final AttachmentType<List<Entry>> DATA = AttachmentRegistry.<List<Entry>>builder().initializer(List::of)
            .persistent(CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodecWithRegistries(CODEC), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_action_cooldowns"));
    public static int remaining(LivingEntity player, Identifier action) {
        return player.getAttachedOrCreate(DATA).stream().filter(e -> e.action.equals(action)).findFirst().map(Entry::remaining).orElse(0);
    }
    public static int duration(LivingEntity player, Identifier action) {
        return player.getAttachedOrCreate(DATA).stream().filter(e -> e.action.equals(action) && e.remaining > 0).findFirst().map(Entry::total).orElse(0);
    }
    public static void start(ServerPlayer player, Identifier action, int ticks) { start(player, action, ticks, ticks); }
    public static void start(ServerPlayer player, Identifier action, int ticks, int total) {
        var entries = new ArrayList<>(player.getAttachedOrCreate(DATA)); entries.removeIf(e -> e.action.equals(action));
        if (ticks > 0) {
            if (entries.size() >= 512) throw new IllegalStateException("Too many action cooldowns");
            entries.add(new Entry(action, ticks, Math.max(0, total)));
        }
        player.setAttached(DATA, List.copyOf(entries));
    }
    public static void tick(ServerPlayer player) {
        if (TimeStopService.isFrozen(player)) return;
        var entries = player.getAttachedOrCreate(DATA); if (entries.isEmpty()) return;
        player.setAttached(DATA, entries.stream().filter(e -> e.remaining > 1)
                .map(e -> new Entry(e.action, e.remaining - 1, e.total)).toList());
    }
    public static void reset(ServerPlayer player) { player.setAttached(DATA, List.of()); }
    public static Identifier legacyId(LivingEntity player, int ability) {
        String prefix = player.getAttachedOrCreate(StandAttachments.PROGRESS).hasTheWorld() ? "the_world_" : "star_platinum_";
        return RipplesFabric.id(switch (ability) {
            case StarPlatinumEntity.PHASE_FINGER -> "star_platinum_star_finger";
            case StarPlatinumEntity.PHASE_INHALE -> "star_platinum_inhale";
            case StarPlatinumEntity.PHASE_ZOOM -> "star_platinum_zoom";
            case 11 -> prefix + "ts_blink";
            case 12 -> "the_world_ts_punch";
            default -> throw new IllegalArgumentException("Unmapped legacy action phase: " + ability);
        });
    }
    public static int remaining(ServerPlayer player, int ability) { return remaining(player, legacyId(player, ability)); }
    public static int duration(ServerPlayer player, int ability) { return duration(player, legacyId(player, ability)); }
    public static void start(ServerPlayer player, int ability, int ticks) { start(player, legacyId(player, ability), ticks); }
    public static void initialize() { }
    private AbilityCooldowns() { }
}
