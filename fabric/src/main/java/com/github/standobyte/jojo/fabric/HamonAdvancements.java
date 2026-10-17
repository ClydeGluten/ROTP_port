package com.github.standobyte.jojo.fabric;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.predicates.*;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import java.util.*;

/** Original Hamon/JoJo criteria with modern codecs; power and charged-source predicates remain authoritative. */
public final class HamonAdvancements {
    private static final StatsTrigger STATS=register("hamon_stats",new StatsTrigger());
    private static final SourceTrigger LAST_HAMON=register("last_hamon",new SourceTrigger());
    private static final PlayerTrigger ABANDON=register("abandon_hamon",new PlayerTrigger());
    private static final ChargeKillTrigger CHARGE_KILL=register("hamon_charge_kill",new ChargeKillTrigger());
    private static final PowerKillTrigger PLAYER_KILL=register("player_killed_entity",new PowerKillTrigger());
    private static final PlayerTrigger AFK=register("afk",new PlayerTrigger());
    private static final Map<UUID,Integer> NO_INPUT=new HashMap<>();
    private static <T extends CriterionTrigger<?>> T register(String id,T trigger) {return Registry.register(BuiltInRegistries.TRIGGER_TYPES,RipplesFabric.id(id),trigger);}
    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(source.getEntity() instanceof ServerPlayer player)PLAYER_KILL.fire(player,entity,source);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers()) {
            var input=player.getLastClientInput();
            boolean moving=input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift();
            int ticks=moving?0:Math.min(NO_INPUT.getOrDefault(player.getUUID(),0)+1,601);NO_INPUT.put(player.getUUID(),ticks);
            if(player.tickCount%60==0 && !player.isInvisible() && ticks>600 && net.minecraft.util.Util.getMillis()-player.getLastActionTime()>30000)AFK.trigger(player);
        }});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->NO_INPUT.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->NO_INPUT.clear());
    }
    public static void onStats(ServerPlayer player) {if(HamonService.hasHamon(player))STATS.fire(player);}
    public static void onAbandon(ServerPlayer player) {ABANDON.trigger(player);}
    // Learning/picking can change effective skill-derived training; reevaluate original stats criteria, without adding invented criteria.
    public static void onLearn(ServerPlayer player,String skill) {onStats(player);}
    public static void onTechnique(ServerPlayer player,String technique) {onStats(player);}
    public static void onLastHamon(ServerPlayer player) {onLastHamon(player,null);}
    public static void onLastHamon(ServerPlayer player,Entity source) {LAST_HAMON.fire(player,source);}
    public static void onChargeKill(ServerPlayer player,LivingEntity killed,Entity chargedEntity,BlockPos chargedBlock) {CHARGE_KILL.fire(player,killed,chargedEntity,chargedBlock);}

    private record StatsPredicate(MinMaxBounds.Ints strength,MinMaxBounds.Ints control,MinMaxBounds.Doubles breathing) {
        static final StatsPredicate ANY=new StatsPredicate(MinMaxBounds.Ints.ANY,MinMaxBounds.Ints.ANY,MinMaxBounds.Doubles.ANY);
        static final Codec<StatsPredicate> CODEC=RecordCodecBuilder.create(i->i.group(
                MinMaxBounds.Ints.CODEC.optionalFieldOf("strength_level",MinMaxBounds.Ints.ANY).forGetter(StatsPredicate::strength),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("control_level",MinMaxBounds.Ints.ANY).forGetter(StatsPredicate::control),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("breathing_training_level",MinMaxBounds.Doubles.ANY).forGetter(StatsPredicate::breathing)).apply(i,StatsPredicate::new));
        boolean matches(ServerPlayer player) {var state=HamonService.state(player);return strength.matches(state.strengthLevel()) && control.matches(state.controlLevel()) && breathing.matches(state.breathingLevel());}
    }
    private record StatsInstance(Optional<Holder<LootItemCondition>> player,StatsPredicate stats) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<StatsInstance> CODEC=RecordCodecBuilder.create(i->i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(StatsInstance::player),
                StatsPredicate.CODEC.optionalFieldOf("hamon_stats",StatsPredicate.ANY).forGetter(StatsInstance::stats)).apply(i,StatsInstance::new));
    }
    private static final class StatsTrigger extends SimpleCriterionTrigger<StatsInstance> {
        @Override public Codec<StatsInstance> codec() {return StatsInstance.CODEC;}
        void fire(ServerPlayer player) {trigger(player,c->c.stats().matches(player));}
    }
    /** Accept modern entity predicates or loot-condition arrays, matching the original AndPredicate alternatives. */
    private record EntityConditions(Optional<EntityPredicate> predicate,List<Holder<LootItemCondition>> conditions) {
        static final Codec<EntityConditions> CODEC=Codec.either(LootItemCondition.CODEC.listOf(),Codec.either(LootItemCondition.CODEC,EntityPredicate.CODEC)).xmap(
                value->value.map(list->new EntityConditions(Optional.empty(),list),either->either.map(condition->new EntityConditions(Optional.empty(),List.of(condition)),entity->new EntityConditions(Optional.of(entity),List.of()))),
                value->value.predicate().isPresent()?Either.right(Either.right(value.predicate().get())):Either.left(value.conditions()));
        boolean matches(ServerPlayer player,Entity entity) {
            if(entity==null)return predicate.isEmpty() && conditions.isEmpty();
            if(predicate.isPresent() && !predicate.get().matches(player,entity))return false;
            var context=EntityPredicate.createContext(player,entity);
            return conditions.stream().allMatch(condition->condition.value().test(context));
        }
    }
    private record SourceInstance(Optional<Holder<LootItemCondition>> player,Optional<EntityConditions> source) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<SourceInstance> CODEC=RecordCodecBuilder.create(i->i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(SourceInstance::player),
                EntityConditions.CODEC.optionalFieldOf("source").forGetter(SourceInstance::source)).apply(i,SourceInstance::new));
    }
    private static final class SourceTrigger extends SimpleCriterionTrigger<SourceInstance> {
        @Override public Codec<SourceInstance> codec() {return SourceInstance.CODEC;}
        void fire(ServerPlayer player,Entity source) {trigger(player,c->c.source().map(p->p.matches(player,source)).orElse(true));}
    }
    private record PowerPredicate(String classification,String type) {
        static final Codec<PowerPredicate> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.STRING.fieldOf("classification").forGetter(PowerPredicate::classification),
                Codec.STRING.optionalFieldOf("type","any").forGetter(PowerPredicate::type)).apply(i,PowerPredicate::new));
        boolean matches(LivingEntity entity) {
            String actual="";
            if(classification.equalsIgnoreCase("non_stand")) {
                if(entity!=null && HamonService.hasHamon(entity))actual="jojo:hamon";
                else if(entity instanceof Player p && VampireService.progress(p).vampire())actual="jojo:vampirism";
            } else if(classification.equalsIgnoreCase("stand")) {
                if(entity instanceof Player p)actual=p.getAttachedOrCreate(StandAttachments.PROGRESS).standId();
            } else return false;
            return type.equalsIgnoreCase("any")?!actual.isEmpty():type.equalsIgnoreCase("none")?actual.isEmpty():type.equals(actual);
        }
    }
    private record KillInstance(Optional<Holder<LootItemCondition>> player,Optional<EntityConditions> entity,
            Optional<DamageSourcePredicate> blow,Optional<PowerPredicate> power,Optional<PowerPredicate> killedPower) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<KillInstance> CODEC=RecordCodecBuilder.create(i->i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(KillInstance::player),
                EntityConditions.CODEC.optionalFieldOf("entity").forGetter(KillInstance::entity),
                DamageSourcePredicate.CODEC.optionalFieldOf("killing_blow").forGetter(KillInstance::blow),
                PowerPredicate.CODEC.optionalFieldOf("power").forGetter(KillInstance::power),
                PowerPredicate.CODEC.optionalFieldOf("killed_power").forGetter(KillInstance::killedPower)).apply(i,KillInstance::new));
    }
    private static final class PowerKillTrigger extends SimpleCriterionTrigger<KillInstance> {
        @Override public Codec<KillInstance> codec() {return KillInstance.CODEC;}
        void fire(ServerPlayer player,LivingEntity killed,DamageSource source) {
            trigger(player,c->c.entity().map(p->p.matches(player,killed)).orElse(true) && c.blow().map(p->p.matches(player,source)).orElse(true)
                    && c.power().map(p->p.matches(player)).orElse(true) && c.killedPower().map(p->p.matches(killed)).orElse(true));
        }
    }
    private record ChargeInstance(Optional<Holder<LootItemCondition>> player,Optional<EntityConditions> killed,
            Optional<PowerPredicate> killedPower,Optional<EntityConditions> charged,Optional<Block> block,
            Optional<StatePropertiesPredicate> state,Optional<LocationPredicate> location) implements SimpleCriterionTrigger.SimpleInstance {
        static final Codec<ChargeInstance> CODEC=RecordCodecBuilder.create(i->i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(ChargeInstance::player),
                EntityConditions.CODEC.optionalFieldOf("killed_entity").forGetter(ChargeInstance::killed),
                PowerPredicate.CODEC.optionalFieldOf("killed_power").forGetter(ChargeInstance::killedPower),
                EntityConditions.CODEC.optionalFieldOf("charged_entity").forGetter(ChargeInstance::charged),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(ChargeInstance::block),
                StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(ChargeInstance::state),
                LocationPredicate.CODEC.optionalFieldOf("location").forGetter(ChargeInstance::location)).apply(i,ChargeInstance::new));
        boolean matches(ServerPlayer player,LivingEntity victim,Entity charge,BlockPos pos) {
            if(!killed.map(p->p.matches(player,victim)).orElse(true) || !killedPower.map(p->p.matches(victim)).orElse(true))return false;
            if(charge!=null)return block.isEmpty() && state.isEmpty() && location.isEmpty() && charged.map(p->p.matches(player,charge)).orElse(true);
            if(pos==null || charged.isPresent())return false;
            var blockState=player.level().getBlockState(pos);
            return block.map(blockState::is).orElse(true) && state.map(p->p.matches(blockState)).orElse(true)
                    && location.map(p->p.matches(player.level(),pos.getX(),pos.getY(),pos.getZ())).orElse(true);
        }
    }
    private static final class ChargeKillTrigger extends SimpleCriterionTrigger<ChargeInstance> {
        @Override public Codec<ChargeInstance> codec() {return ChargeInstance.CODEC;}
        void fire(ServerPlayer player,LivingEntity victim,Entity charged,BlockPos pos) {trigger(player,c->c.matches(player,victim,charged,pos));}
    }
    private HamonAdvancements() { }
}
