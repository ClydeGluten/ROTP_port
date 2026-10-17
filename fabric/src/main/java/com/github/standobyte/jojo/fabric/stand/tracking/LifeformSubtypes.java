package com.github.standobyte.jojo.fabric.stand.tracking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Original EntitySubtype catalog, including its sole extra subtype: Coco Jumbo with Mr President. */
public final class LifeformSubtypes {
    public record Key(Identifier entity, Optional<String> variant) implements Comparable<Key> {
        public static final Codec<Key> CODEC = Codec.STRING.comapFlatMap(value -> parse(value)
                .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid lifeform subtype")), Key::toString);
        public Key { Objects.requireNonNull(entity); variant = variant.map(v -> v.toLowerCase(Locale.ROOT)); }
        public static Key base(Identifier type) { return new Key(type, Optional.empty()); }
        public static Optional<Key> parse(String value) {
            if (value == null || value.length() > 256) return Optional.empty();
            int split = value.indexOf('#'); String base = split < 0 ? value : value.substring(0, split);
            Identifier entity = Identifier.tryParse(base); if (entity == null) return Optional.empty();
            if (split < 0) return Optional.of(base(entity));
            String variant = value.substring(split + 1).toLowerCase(Locale.ROOT);
            if (variant.isEmpty() || variant.length() > 64 || !variant.matches("[a-z0-9_.-]+")) return Optional.empty();
            return Optional.of(new Key(entity, Optional.of(variant)));
        }
        @Override public String toString() { return entity + variant.map(v -> "#" + v).orElse(""); }
        @Override public int compareTo(Key key) { return toString().compareTo(key.toString()); }
    }
    private record Variant(Consumer<Entity> initialize, Predicate<Entity> matches) { }
    private static final Map<Key, Variant> VARIANTS = new LinkedHashMap<>();
    private static final Map<Level, Map<Key, Optional<Entity>>> PROTOTYPES = new WeakHashMap<>();
    private static final Identifier COCO = Identifier.fromNamespaceAndPath("jojo", "coco_jumbo_turtle");
    private static final Set<String> DISABLED_NAMESPACES = Set.of("rotp_zbc", "rotp_harvest");
    private static final Set<String> DISABLED = Set.of(
            "twilightforest:quest_ram", "twilightforest:wraith", "twilightforest:redcap", "twilightforest:redcap_sapper",
            "twilightforest:death_tome", "twilightforest:minoshroom", "twilightforest:minotaur", "twilightforest:maze_slime",
            "twilightforest:mist_wolf", "twilightforest:tower_golem", "twilightforest:blockchain_goblin", "twilightforest:goblin_knight_upper",
            "twilightforest:goblin_knight_lower", "twilightforest:knight_phantom", "twilightforest:yeti", "twilightforest:snow_guardian",
            "twilightforest:stable_ice_core", "twilightforest:unstable_ice_core", "twilightforest:snow_queen", "twilightforest:ice_crystal",
            "twilightforest:troll", "twilightforest:adherent", "twilightforest:roving_cube", "twilightforest:plateau_boss",
            "alexsmobs:centipede_head", "alexsmobs:guster", "alexsmobs:enderiophage", "alexsmobs:mimicube", "rotp_zkq:sheer_heart");
    /** Plan52 calls this after registering the actual entity and its actor-owned Mr President power. */
    public static void registerCocoJumbo(Consumer<Entity> grantMrPresident, Predicate<Entity> hasMrPresident) {
        var key = new Key(COCO, Optional.of("stand"));
        if (VARIANTS.putIfAbsent(key, new Variant(grantMrPresident, hasMrPresident)) != null)
            throw new IllegalStateException("Coco Jumbo lifeform subtype already registered");
    }
    public static boolean supported(Key key) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(key.entity) && (key.variant.isEmpty() || VARIANTS.containsKey(key));
    }
    public static Stream<Key> values() {
        return Stream.concat(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Key::base), VARIANTS.keySet().stream());
    }
    public static List<Key> matching(Entity entity) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        var result = new ArrayList<Key>(); result.add(Key.base(type));
        VARIANTS.forEach((key, variant) -> { if (key.entity.equals(type) && variant.matches.test(entity)) result.add(key); });
        return List.copyOf(result);
    }
    public static Optional<Entity> create(Key key, Level level) {
        if (!supported(key)) return Optional.empty();
        try {
            Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(key.entity).create(level, EntitySpawnReason.COMMAND);
            if (entity == null) return Optional.empty();
            if (key.variant.isPresent()) VARIANTS.get(key).initialize.accept(entity);
            return Optional.of(entity);
        } catch (RuntimeException invalidFactory) { return Optional.empty(); }
    }
    public static boolean isValid(Key key, Level level) {
        if (!supported(key)) return false;
        return PROTOTYPES.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(key, ignored -> create(key, level))
                .filter(LifeformSubtypes::isValid).isPresent();
    }
    public static boolean isValid(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        EntityType<?> type = entity.getType(); Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (type.builtInRegistryHolder().is(EntityTypeTags.UNDEAD) || type.builtInRegistryHolder().is(EntityTypeTags.ILLAGER)
                || type == EntityTypes.TRADER_LLAMA || !type.canSummon()) return false;
        if (id.equals(COCO)) {
            Variant stand = VARIANTS.get(new Key(COCO, Optional.of("stand")));
            if (stand == null || !stand.matches.test(entity)) return false;
        }
        if (type == EntityTypes.SLIME || type == EntityTypes.MAGMA_CUBE) return false;
        // Old FlyingEntity's vanilla members were all separately excluded (Ghast or undead Phantom).
        if (!(mob instanceof AmbientCreature || mob instanceof PathfinderMob || mob instanceof Slime || mob instanceof Ghast)) return false;
        if (mob instanceof Npc || mob instanceof Merchant || mob instanceof AbstractGolem || mob instanceof PatrollingMonster
                || mob instanceof Ghast || mob instanceof Blaze || mob instanceof Vex || mob instanceof Creeper
                || mob instanceof Enderman || mob instanceof AbstractPiglin || mob instanceof Guardian) return false;
        return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() < 7.5 && mob.getMaxHealth() <= 60
                && !DISABLED_NAMESPACES.contains(id.getNamespace()) && !DISABLED.contains(id.toString());
    }
    public static void clearRuntime() { PROTOTYPES.clear(); }
    private LifeformSubtypes() { }
}
