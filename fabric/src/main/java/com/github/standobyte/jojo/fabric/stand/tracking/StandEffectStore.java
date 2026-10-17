package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.StandEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Source query/lifetime facade; StandEffects remains the single persistent owner and tick lifecycle. */
public final class StandEffectStore {
    public record TargetedEffect(LivingEntity owner, StandEffects.Entry effect) { }
    private static final Map<LivingEntity, List<ItemTrackingPayload.EffectView>> CLIENT = new WeakHashMap<>();
    private static final Map<LivingEntity, List<ItemTrackingPayload.EffectView>> LAST_SENT = new WeakHashMap<>();
    private static final Map<LivingEntity, Boolean> OWNERS = new WeakHashMap<>();
    private static List<StandEffects.Entry> entries(LivingEntity owner) {
        if (!owner.level().isClientSide()) return StandEffects.entries(owner);
        return CLIENT.getOrDefault(owner, List.of()).stream().map(e ->
                new StandEffects.Entry(e.id(), e.type(), e.target(), e.ticks(), new CompoundTag())).toList();
    }
    public static Stream<StandEffects.Entry> ofType(LivingEntity owner, Identifier type) {
        return entries(owner).stream().filter(e -> e.type().equals(type));
    }
    public static Optional<StandEffects.Entry> getOrCreate(LivingEntity owner, Identifier type, Entity target,
                                                         Supplier<CompoundTag> initialData) {
        if (owner.level().isClientSide()) return Optional.empty();
        OWNERS.put(owner, true);
        return Optional.of(StandEffects.find(owner, type, target == null ? null : target.getUUID())
                .orElseGet(() -> StandEffects.add(owner, type, target, initialData.get())));
    }
    public static Optional<StandEffects.Entry> getOrCreate(LivingEntity owner, Identifier type, Supplier<CompoundTag> initialData) {
        if (owner.level().isClientSide()) return Optional.empty();
        OWNERS.put(owner, true);
        return Optional.of(ofType(owner, type).findFirst().orElseGet(() -> StandEffects.add(owner, type, null, initialData.get())));
    }
    public static Optional<StandEffects.Entry> byId(LivingEntity owner, UUID id) {
        return entries(owner).stream().filter(e -> e.id().equals(id)).findFirst();
    }
    public static void removeType(LivingEntity owner, Identifier type) {
        ofType(owner, type).toList().forEach(e -> StandEffects.remove(owner, e.id()));
    }
    public static Entity target(LivingEntity owner, StandEffects.Entry effect) {
        if (!owner.level().isClientSide()) return StandEffects.target(owner, effect);
        return CLIENT.getOrDefault(owner, List.of()).stream().filter(e -> e.id().equals(effect.id()))
                .map(e -> owner.level().getEntity(e.targetId())).filter(java.util.Objects::nonNull)
                .filter(entity -> effect.target().filter(entity.getUUID()::equals).isPresent()).findFirst().orElse(null);
    }
    public static Stream<StandEffects.Entry> targetsInRange(LivingEntity owner, Identifier type, double range) {
        double squared = range * range;
        return ofType(owner, type).filter(e -> {
            Entity target = target(owner, e);
            return target != null && (range <= 0 || target.distanceToSqr(owner) < squared);
        });
    }
    /** Source maximizes the dot product, without an angle threshold or a line-of-sight test. */
    public static Optional<StandEffects.Entry> getTargetLookedAt(LivingEntity owner, Identifier type, double range) {
        return getTargetLookedAt(owner, targetsInRange(owner, type, range));
    }
    public static Optional<StandEffects.Entry> getTargetLookedAt(LivingEntity viewer, Stream<StandEffects.Entry> entries) {
        var eye = viewer.getEyePosition();
        var look = viewer.getLookAngle();
        return entries.filter(e -> target(viewer, e) != null).max(Comparator.comparingDouble(e ->
                look.dot(target(viewer, e).getBoundingBox().getCenter().subtract(eye).normalize())));
    }
    /** Callers pass loaded owners; no global world scan or duplicate inverse persistence is introduced. */
    public static Stream<TargetedEffect> targeting(Entity target, Identifier type, Stream<? extends LivingEntity> owners) {
        return owners.filter(owner -> owner.level() == target.level()).flatMap(owner -> ofType(owner, type)
                .filter(e -> e.target().filter(target.getUUID()::equals).isPresent()).map(e -> new TargetedEffect(owner, e)));
    }
    public static Stream<TargetedEffect> targeting(Entity target, Identifier type) {
        return targeting(target, type, List.copyOf(OWNERS.keySet()).stream().filter(owner -> !owner.isRemoved()));
    }
    /** Optional finite duration for concrete effects. The ordinary source effects choose their own policy. */
    public static StandEffects.Handler expiring(int duration, StandEffects.Handler delegate) {
        if (duration < 1) throw new IllegalArgumentException("Effect duration must be positive");
        return new StandEffects.Handler() {
            public void start(LivingEntity user, StandEffects.Entry effect, Entity target) { delegate.start(user, effect, target); }
            public StandEffects.Entry tick(LivingEntity user, StandEffects.Entry effect, Entity target) {
                return effect.ticks() >= duration ? null : delegate.tick(user, effect, target);
            }
            public void stop(LivingEntity user, StandEffects.Entry effect, Entity target) { delegate.stop(user, effect, target); }
            public boolean needsTarget() { return delegate.needsTarget(); }
            public boolean shouldClearTarget(Entity target) { return delegate.shouldClearTarget(target); }
            public boolean removeOn(StandEffects.Removal reason) { return delegate.removeOn(reason); }
        };
    }
    public static void receive(Level level, ItemTrackingPayload.Effects payload) {
        if (level == null || !level.isClientSide() || !level.dimension().identifier().equals(payload.dimension())) return;
        Entity owner = level.getEntity(payload.ownerId());
        if (owner instanceof LivingEntity living && owner.getUUID().equals(payload.owner())) {
            CLIENT.put(living, payload.entries());
            if (payload.entries().isEmpty()) OWNERS.remove(living); else OWNERS.put(living, true);
        }
    }
    private static ItemTrackingPayload.Effects snapshot(LivingEntity owner) {
        var entries = StandEffects.entries(owner).stream().map(e -> {
            Entity target = target(owner, e);
            return new ItemTrackingPayload.EffectView(e.id(), e.type(), e.target(), target == null ? -1 : target.getId(), e.ticks());
        }).toList();
        return new ItemTrackingPayload.Effects(owner.getUUID(), owner.getId(), owner.level().dimension().identifier(), entries);
    }
    public static void syncTo(LivingEntity owner, ServerPlayer recipient) {
        if (!owner.level().isClientSide() && ServerPlayNetworking.canSend(recipient, ItemTrackingPayload.Effects.TYPE))
            ServerPlayNetworking.send(recipient, snapshot(owner));
    }
    /** Root calls after the existing effect tick; this never ticks server gameplay a second time. */
    public static void tickPresentation(LivingEntity owner) {
        if (owner.level().isClientSide()) {
            var previous = CLIENT.get(owner);
            if (previous != null) CLIENT.put(owner, previous.stream().map(e -> new ItemTrackingPayload.EffectView(
                    e.id(), e.type(), e.target(), e.targetId(), Math.min(Integer.MAX_VALUE - 1, e.ticks()) + 1)).toList());
            return;
        }
        if (StandEffects.entries(owner).isEmpty()) OWNERS.remove(owner); else OWNERS.put(owner, true);
        var payload = snapshot(owner); var previous = LAST_SENT.get(owner);
        // Source sends creation/removal/target changes; elapsed ticks advance locally between those packets.
        boolean changed = previous == null || previous.size() != payload.entries().size();
        if (!changed) for (int i = 0; i < previous.size(); i++) {
            var a = previous.get(i); var b = payload.entries().get(i);
            if (!a.id().equals(b.id()) || !a.type().equals(b.type()) || !a.target().equals(b.target()) || a.targetId() != b.targetId()) { changed = true; break; }
        }
        if (!changed) return;
        LAST_SENT.put(owner, payload.entries());
        for (ServerPlayer recipient : PlayerLookup.tracking(owner)) if (ServerPlayNetworking.canSend(recipient, ItemTrackingPayload.Effects.TYPE))
            ServerPlayNetworking.send(recipient, payload);
        if (owner instanceof ServerPlayer player && ServerPlayNetworking.canSend(player, ItemTrackingPayload.Effects.TYPE)) ServerPlayNetworking.send(player, payload);
    }
    /** Root calls after StandEffects add/remove; source inverse targeting is available immediately. */
    public static void ownerChanged(LivingEntity owner) { tickPresentation(owner); }
    public static void clearRuntime() { CLIENT.clear(); LAST_SENT.clear(); OWNERS.clear(); }
    public static void initialize() { StandEffects.initialize(); }
    private StandEffectStore() { }
}
