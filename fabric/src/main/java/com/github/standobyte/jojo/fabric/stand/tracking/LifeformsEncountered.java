package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import java.util.*;

/** Persistent encounter set and original throttled biome/structure spawn eligibility. */
public final class LifeformsEncountered {
    public record State(List<LifeformSubtypes.Key> encountered) {
        public static final State EMPTY = new State(List.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(s -> 1),
                LifeformSubtypes.Key.CODEC.listOf(0, 4096).fieldOf("encountered").forGetter(State::encountered))
                .apply(i, (version, values) -> new State(values)));
        public State { encountered = List.copyOf(new TreeSet<>(encountered)); }
    }
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of((b, s) -> {
        b.writeVarInt(s.encountered.size()); s.encountered.forEach(key -> b.writeUtf(key.toString(), 256));
    }, b -> {
        int size = b.readVarInt(); if (size < 0 || size > 4096) throw new DecoderException("Too many encountered lifeforms");
        var values = new ArrayList<LifeformSubtypes.Key>();
        for (int i = 0; i < size; i++) values.add(LifeformSubtypes.Key.parse(b.readUtf(256)).orElseThrow(() -> new DecoderException("Invalid lifeform subtype")));
        return new State(values);
    });
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .persistent(State.CODEC).copyOnDeath().syncWith(STREAM, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("encountered_lifeforms"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Identifier>> NATIVE_STREAM = StreamCodec.of((b, s) -> {
        b.writeVarInt(s.size()); s.forEach(id -> b.writeUtf(id.toString(), 256));
    }, b -> {
        int size = b.readVarInt(); if (size < 0 || size > 4096) throw new DecoderException("Too many native lifeforms");
        var result = new ArrayList<Identifier>();
        for (int i = 0; i < size; i++) {
            var id = Identifier.tryParse(b.readUtf(256)); if (id == null) throw new DecoderException("Invalid native lifeform"); result.add(id);
        }
        return List.copyOf(result);
    });
    public static final AttachmentType<List<Identifier>> NATIVE = AttachmentRegistry.<List<Identifier>>builder().initializer(List::of)
            .syncWith(NATIVE_STREAM, AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("native_lifeforms"));
    private static final Map<LivingEntity, NativeState> RUNTIME = new WeakHashMap<>();
    private static final class NativeState { int delay; boolean pending; Identifier dimension; }
    private static final class SpawnRulesNotRandom extends LegacyRandomSource {
        SpawnRulesNotRandom() { super(0); }
        @Override public int next(int bits) { return 0; }
    }
    public static List<LifeformSubtypes.Key> values(LivingEntity user) { return user.getAttachedOrCreate(DATA).encountered; }
    public static boolean contains(LivingEntity user, LifeformSubtypes.Key key) { return LifeformSubtypes.supported(key) && values(user).contains(key); }
    public static boolean encounter(LivingEntity user, Entity entity) {
        if (user.level().isClientSide() || entity.level() != user.level() || entity.isRemoved() || entity.distanceToSqr(user) > 144) return false;
        var next = new TreeSet<>(values(user)); boolean changed = next.addAll(LifeformSubtypes.matching(entity));
        if (changed && next.size() <= 4096) user.setAttached(DATA, new State(List.copyOf(next))); else return false;
        return true;
    }
    public static void unlockAll(LivingEntity user) {
        if (user.level().isClientSide()) return;
        var next = new TreeSet<>(values(user)); LifeformSubtypes.values().filter(key -> LifeformSubtypes.isValid(key, user.level())).forEach(next::add);
        if (next.size() > 4096) throw new IllegalStateException("Lifeform encounter bounds exceeded");
        user.setAttached(DATA, new State(List.copyOf(next)));
    }
    public static boolean eligible(LivingEntity user, LifeformSubtypes.Key key) { return contains(user, key) && LifeformSubtypes.isValid(key, user.level()); }
    public static void updateNative(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) return;
        NativeState state = RUNTIME.computeIfAbsent(user, ignored -> new NativeState());
        Identifier dimension = level.dimension().identifier();
        if (!dimension.equals(state.dimension)) { state.dimension = dimension; state.delay = 0; user.setAttached(NATIVE, List.of()); }
        if (state.delay > 0) return;
        if (!user.onGround() && !user.isInWater()) { state.pending = true; return; }
        state.pending = false; state.delay = 20;
        var known = new HashSet<Identifier>(); values(user).forEach(key -> known.add(key.entity()));
        var nativeTypes = new LinkedHashSet<Identifier>(); var pos = user.blockPosition(); var random = new SpawnRulesNotRandom();
        for (MobCategory category : MobCategory.values()) {
            // Modern generator's getMobsAt includes structure overrides, including Nether fortress monsters.
            var spawns = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), category, pos);
            for (var weighted : spawns.unwrap()) {
                EntityType<?> type = weighted.value().type(); Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (known.contains(id) && LifeformSubtypes.isValid(LifeformSubtypes.Key.base(id), level)
                        && ((SpawnPlacements.getPlacementType(type) == SpawnPlacementTypes.IN_WATER) == level.getFluidState(pos).is(FluidTags.WATER))
                        && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.SPAWNER, pos, random)) nativeTypes.add(id);
            }
        }
        user.setAttached(NATIVE, List.copyOf(nativeTypes));
    }
    public static boolean isNative(LivingEntity user, EntityType<?> type) {
        if (!user.level().isClientSide()) updateNative(user);
        return user.getAttachedOrCreate(NATIVE).contains(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
    public static void tick(LivingEntity user) {
        NativeState state = RUNTIME.get(user); if (state == null) return;
        if (state.delay > 0) state.delay--;
        if (state.delay == 0 && state.pending && user.isAlive()) updateNative(user);
    }
    public static void clearRuntime() { RUNTIME.clear(); LifeformSubtypes.clearRuntime(); }
    public static void initialize() { }
    private LifeformsEncountered() { }
}
