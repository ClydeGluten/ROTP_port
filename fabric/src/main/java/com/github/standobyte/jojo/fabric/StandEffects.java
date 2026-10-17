package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Persistent source StandEffectsTracker lifecycle. A missing loaded target is not a deleted UUID. */
public final class StandEffects {
    public enum Removal { DEATH, LOGOUT, STAND_CHANGED, OWNER_REMOVED }
    public record Entry(UUID id, Identifier type, Optional<UUID> target, int ticks, CompoundTag data) {
        private static final Codec<CompoundTag> PAYLOAD = CompoundTag.CODEC.validate(tag -> tag.sizeInBytes() <= 65536
                ? DataResult.success(tag) : DataResult.error(() -> "Stand effect data too large"));
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id), Identifier.CODEC.fieldOf("type").forGetter(Entry::type),
                UUIDUtil.CODEC.optionalFieldOf("target").forGetter(Entry::target),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("ticks", 0).forGetter(Entry::ticks),
                PAYLOAD.optionalFieldOf("data", new CompoundTag()).forGetter(Entry::data)).apply(i, Entry::new));
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(target); data = data.copy(); }
        @Override public CompoundTag data() { return data.copy(); }
        public Entry withData(CompoundTag value) { return new Entry(id, type, target, ticks, value); }
        public Entry withTarget(Entity value) { return new Entry(id, type, Optional.ofNullable(value).map(Entity::getUUID), ticks, data); }
        Entry nextTick() { return new Entry(id, type, target, Math.min(ticks, Integer.MAX_VALUE - 1) + 1, data); }
    }
    public record State(List<Entry> entries) {
        public static final State EMPTY = new State(List.of());
        public State {
            var unique = new LinkedHashMap<UUID, Entry>();
            for (var entry : entries) unique.putIfAbsent(entry.id(), entry);
            entries = List.copyOf(unique.values());
        }
    }
    public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(s -> 1),
            Entry.CODEC.listOf(0, 128).fieldOf("entries").forGetter(State::entries)).apply(i, (version, entries) -> new State(entries)));
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .persistent(CODEC).copyOnDeath().buildAndRegister(RipplesFabric.id("stand_continuous_effects"));

    /** Concrete registered effects implement gameplay; unknown saved types remain intact for later modules. */
    public interface Handler {
        void start(LivingEntity user, Entry effect, Entity target);
        Entry tick(LivingEntity user, Entry effect, Entity target);
        void stop(LivingEntity user, Entry effect, Entity target);
        boolean needsTarget();
        default boolean shouldClearTarget(Entity target) { return target instanceof LivingEntity living && living.isDeadOrDying(); }
        default boolean removeOn(Removal reason) { return true; }
    }
    private static final Map<Identifier, Handler> HANDLERS = new HashMap<>();
    private static final Map<LivingEntity, Set<UUID>> STARTED = new WeakHashMap<>();
    public static void register(Identifier type, Handler handler) {
        if (HANDLERS.putIfAbsent(type, Objects.requireNonNull(handler)) != null) throw new IllegalStateException("Duplicate Stand effect: " + type);
    }
    public static List<Entry> entries(LivingEntity user) { return user.getAttachedOrCreate(DATA).entries; }
    public static Optional<Entry> find(LivingEntity user, Identifier type, UUID target) {
        return entries(user).stream().filter(e -> e.type.equals(type) && e.target.equals(Optional.ofNullable(target))).findFirst();
    }
    public static Entity target(LivingEntity user, Entry effect) {
        return user.level() instanceof ServerLevel level ? effect.target.map(level::getEntity).orElse(null) : null;
    }
    public static Entry add(LivingEntity user, Identifier type, Entity target, CompoundTag data) {
        if (user.level().isClientSide() || !HANDLERS.containsKey(type)) throw new IllegalArgumentException("Unregistered server Stand effect: " + type);
        if (entries(user).size() >= 128 || data.sizeInBytes() > 65536) throw new IllegalStateException("Stand effect bounds exceeded");
        var entry = new Entry(UUID.randomUUID(), type, Optional.ofNullable(target).map(Entity::getUUID), 0, data);
        var next = new ArrayList<>(entries(user)); next.add(entry); user.setAttached(DATA, new State(next));
        start(user, entry); com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore.ownerChanged(user); return entry;
    }
    private static void start(LivingEntity user, Entry entry) {
        var handler = HANDLERS.get(entry.type);
        if (handler != null && STARTED.computeIfAbsent(user, ignored -> new HashSet<>()).add(entry.id)) handler.start(user, entry, target(user, entry));
    }
    public static void remove(LivingEntity user, UUID id) {
        if (user.level().isClientSide()) return;
        var entry = entries(user).stream().filter(e -> e.id.equals(id)).findFirst().orElse(null); if (entry == null) return;
        // Remove before callback so recursive cleanup/part-return cannot run twice.
        user.setAttached(DATA, new State(entries(user).stream().filter(e -> !e.id.equals(id)).toList()));
        var started = STARTED.get(user); if (started != null) started.remove(id);
        var handler = HANDLERS.get(entry.type); if (handler != null) handler.stop(user, entry, target(user, entry));
        com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore.ownerChanged(user);
    }
    public static void tick(LivingEntity user) {
        if (user.level().isClientSide() || TimeStopService.isFrozen(user)) return;
        for (var snapshot : List.copyOf(entries(user))) {
            var handler = HANDLERS.get(snapshot.type); if (handler == null) continue;
            if (entries(user).stream().noneMatch(e -> e.id.equals(snapshot.id))) continue;
            start(user, snapshot);
            Entity target = target(user, snapshot);
            var next = snapshot.nextTick();
            if (target != null && handler.shouldClearTarget(target)) {
                next = next.withTarget(null); target = null;
                var cleared = new ArrayList<>(entries(user));
                for (int j = 0; j < cleared.size(); j++) if (cleared.get(j).id.equals(snapshot.id)) { cleared.set(j, next); break; }
                user.setAttached(DATA, new State(cleared));
            }
            if (next.target.isEmpty() && handler.needsTarget()) { remove(user, snapshot.id); continue; }
            // An unloaded/dead-but-retained target keeps its UUID; concrete effects decide their own policy.
            if (target != null && !target.isAlive()) target = null;
            next = handler.tick(user, next, target);
            if (next == null) { remove(user, snapshot.id); continue; }
            if (!next.id.equals(snapshot.id) || !next.type.equals(snapshot.type) || next.data.sizeInBytes() > 65536)
                throw new IllegalStateException("Effect handler changed identity or exceeded bounds");
            var updated = new ArrayList<>(entries(user));
            for (int j = 0; j < updated.size(); j++) if (updated.get(j).id.equals(snapshot.id)) { updated.set(j, next); break; }
            user.setAttached(DATA, new State(updated));
        }
    }
    public static void cleanup(LivingEntity user, Removal reason) {
        if (user.level().isClientSide()) return;
        for (var effect : List.copyOf(entries(user))) {
            var handler = HANDLERS.get(effect.type);
            if (handler != null && handler.removeOn(reason)) remove(user, effect.id);
        }
        if (reason == Removal.LOGOUT || reason == Removal.OWNER_REMOVED) STARTED.remove(user);
    }
    public static void clearRuntime() { STARTED.clear(); }
    public static void initialize() { }
    private StandEffects() { }
}
