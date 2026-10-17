package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/** The original vampire criteria and custom statistics, with modern codecs and unchanged predicates. */
public final class VampireAdvancements {
    public static final Identifier PEOPLE_DRAINED = stat("vampire_people_drained");
    public static final Identifier ANIMALS_DRAINED = stat("vampire_animals_drained");
    public static final Identifier ZOMBIES_CREATED = stat("vampire_zombies_created");
    public static final Identifier ZOMBIES_SUMMONED = stat("vampire_zombies_summoned");
    private static final PowerTrigger GET_POWER = register("get_power", new PowerTrigger());
    private static final DrainTrigger DRAINED = register("vampire_people_drained", new DrainTrigger());
    private static final PlayerTrigger CURED = register("cure_vampirism", new PlayerTrigger());
    private static final PlayerTrigger COFFIN_SLEEP = register("coffin_sleep", new PlayerTrigger());
    private static final PlayerTrigger MASK_SUICIDE = register("mask_suicide", new PlayerTrigger());
    private static final PlayerTrigger SCARF_HAMON_DAMAGE = register("vampire_hamon_damage_scarf", new PlayerTrigger());
    private static final MaskTrigger MASK_DESTROYED = register("destroy_stone_mask", new MaskTrigger());
    private static Identifier stat(String name) {
        var id = RipplesFabric.id(name);
        Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.get(id, StatFormatter.DEFAULT);
        return id;
    }
    private static <T extends CriterionTrigger<?>> T register(String name, T trigger) {
        return Registry.register(BuiltInRegistries.TRIGGER_TYPES, RipplesFabric.id(name), trigger);
    }
    public static void initialize() { }
    public static void onHamonAcquired(ServerPlayer player) { GET_POWER.fire(player, "non_stand", "jojo:hamon"); }
    public static void onTransformation(ServerPlayer player) { GET_POWER.fire(player, "non_stand", "jojo:vampirism"); }
    public static void onCured(ServerPlayer player) { CURED.trigger(player); }
    public static void onCoffinSleep(ServerPlayer player) { COFFIN_SLEEP.trigger(player); }
    public static void onMaskSuicide(ServerPlayer player) { MASK_SUICIDE.trigger(player); }
    public static void onScarfHamonDamage(ServerPlayer player) { SCARF_HAMON_DAMAGE.trigger(player); }
    public static void onMaskDestroyed(ServerPlayer player, Block block, ItemStack tool, ItemStack mask) { MASK_DESTROYED.fire(player, block, tool, mask); }
    public static void onMaskDestroyed(ServerPlayer player, ItemStack tool) {
        onMaskDestroyed(player, BuiltInRegistries.BLOCK.getValue(RipplesFabric.id("stone_mask")), tool, new ItemStack(VampireItems.STONE_MASK));
    }
    public static void onSummoned(ServerPlayer player, int count) { player.awardStat(ZOMBIES_SUMMONED, count); }
    public static void onDrainedKill(ServerPlayer player, LivingEntity victim, boolean zombieCreated) {
        boolean human = victim instanceof Player || victim instanceof Npc || victim instanceof AbstractIllager;
        player.awardStat(human ? PEOPLE_DRAINED : ANIMALS_DRAINED);
        if (zombieCreated) player.awardStat(ZOMBIES_CREATED);
        DRAINED.fire(player, player.getStats().getValue(Stats.CUSTOM.get(PEOPLE_DRAINED)),
                player.getStats().getValue(Stats.CUSTOM.get(ZOMBIES_CREATED)));
    }
    private record PowerPredicate(String classification, String type) {
        static final Codec<PowerPredicate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("classification").forGetter(PowerPredicate::classification),
                Codec.STRING.optionalFieldOf("type", "any").forGetter(PowerPredicate::type)
        ).apply(instance, PowerPredicate::new));
        boolean matches(String classification, String type) {
            return this.classification.equalsIgnoreCase(classification) && (this.type.equalsIgnoreCase("any")
                    ? type != null : this.type.equalsIgnoreCase("none") ? type == null : this.type.equals(type));
        }
    }
    private record PowerInstance(Optional<Holder<LootItemCondition>> player, Optional<PowerPredicate> jojopower) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<PowerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(PowerInstance::player),
                PowerPredicate.CODEC.optionalFieldOf("jojopower").forGetter(PowerInstance::jojopower)
        ).apply(instance, PowerInstance::new));
    }
    private static final class PowerTrigger extends SimpleCriterionTrigger<PowerInstance> {
        @Override public Codec<PowerInstance> codec() { return PowerInstance.CODEC; }
        void fire(ServerPlayer player, String classification, String type) {
            trigger(player, condition -> condition.jojopower().map(power -> power.matches(classification, type)).orElse(true));
        }
    }
    private record DrainInstance(Optional<Holder<LootItemCondition>> player, MinMaxBounds.Ints people,
                                 MinMaxBounds.Ints zombies) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<DrainInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(DrainInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("people_drained", MinMaxBounds.Ints.ANY).forGetter(DrainInstance::people),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("zombies_created", MinMaxBounds.Ints.ANY).forGetter(DrainInstance::zombies)
        ).apply(instance, DrainInstance::new));
    }
    private static final class DrainTrigger extends SimpleCriterionTrigger<DrainInstance> {
        @Override public Codec<DrainInstance> codec() { return DrainInstance.CODEC; }
        void fire(ServerPlayer player, int people, int zombies) { trigger(player, condition -> condition.people().matches(people) && condition.zombies().matches(zombies)); }
    }
    private record MaskInstance(Optional<Holder<LootItemCondition>> player, Optional<Block> block,
                                Optional<ItemPredicate> tool, Optional<ItemPredicate> mask) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<MaskInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(MaskInstance::player),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(MaskInstance::block),
                ItemPredicate.CODEC.optionalFieldOf("item_used").forGetter(MaskInstance::tool),
                ItemPredicate.CODEC.optionalFieldOf("stone_mask_item").forGetter(MaskInstance::mask)
        ).apply(instance, MaskInstance::new));
    }
    private static final class MaskTrigger extends SimpleCriterionTrigger<MaskInstance> {
        @Override public Codec<MaskInstance> codec() { return MaskInstance.CODEC; }
        void fire(ServerPlayer player, Block block, ItemStack tool, ItemStack mask) {
            trigger(player, condition -> condition.block().map(expected -> expected == block).orElse(true)
                    && condition.tool().map(predicate -> predicate.test(tool)).orElse(true)
                    && condition.mask().map(predicate -> predicate.test(mask)).orElse(true));
        }
    }
    private VampireAdvancements() { }
}
