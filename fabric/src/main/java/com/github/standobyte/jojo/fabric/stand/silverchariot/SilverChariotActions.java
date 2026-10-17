package com.github.standobyte.jojo.fabric.stand.silverchariot;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import java.util.List;

/** The eight original Silver Chariot registrations. Shared registry owns costs, parts, unlocks and input leases. */
public final class SilverChariotActions {
    public static final net.minecraft.core.particles.SimpleParticleType FLAME_PARTICLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            RipplesFabric.id("flame"), net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());
    public static final String ID = "jojo:silver_chariot";
    public static final EntityType<SilverChariotEntity> STAND = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("silver_chariot"),
            EntityType.Builder.<SilverChariotEntity>of(SilverChariotEntity::new, MobCategory.MISC).sized(.6F, 1.95F).noSave().noSummon().clientTrackingRange(10).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("silver_chariot"))));
    public static final EntityType<SilverChariotRapierEntity> RAPIER = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("sc_rapier"),
            EntityType.Builder.<SilverChariotRapierEntity>of(SilverChariotRapierEntity::new, MobCategory.MISC).sized(.125F, .125F).noSave().noSummon().clientTrackingRange(10).updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("sc_rapier"))));
    public static final EntityType<SilverChariotFlameSwingEntity> FLAME = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("sc_flame"),
            EntityType.Builder.<SilverChariotFlameSwingEntity>of(SilverChariotFlameSwingEntity::new, MobCategory.MISC).sized(.0625F, .0625F).noSave().noSummon().clientTrackingRange(10).updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("sc_flame"))));
    public static final Identifier ATTACK = id("attack"), UNARMED = id("no_rapier_attack"), BARRAGE = id("barrage"),
            SWEEP = id("sweeping_attack"), DASH = id("dash_attack"), LAUNCH = id("rapier_launch"), BLOCK = id("block"), ARMOR = id("take_off_armor");
    private static boolean initialized;
    private static Identifier id(String suffix) { return RipplesFabric.id("silver_chariot_" + suffix); }
    private static StandActionRegistry.Builder definition(Identifier action) { return StandActionRegistry.Definition.builder(action, ID); }
    public static void initialize() {
        if (initialized) return; initialized = true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(SilverChariotEntity::tickSuspendedTargets);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SilverChariotEntity.clearRuntime());
        var arms = StandParts.Part.ARMS; var body = StandParts.Part.MAIN_BODY;
        StandActionRegistry.registerReplacement(ID, SilverChariotActions::replace);
        register(definition(ATTACK).hotbar(StandActionRegistry.Hotbar.LEFT, 0).parts(arms).shift(DASH).cost(10, 0)
                .recoveryFollowupOf(ATTACK, UNARMED).build(), SilverChariotEntity.Kind.LIGHT);
        register(definition(UNARMED).parts(arms).cost(10, 0).recoveryFollowupOf(ATTACK, UNARMED).build(), SilverChariotEntity.Kind.UNARMED);
        register(definition(BARRAGE).hotbar(StandActionRegistry.Hotbar.LEFT, 1).parts(arms).shift(DASH).hold(Integer.MAX_VALUE, 0, false).cost(0, 4)
                .recoveryFollowupOf(ATTACK, UNARMED).build(), SilverChariotEntity.Kind.BARRAGE);
        register(definition(DASH).parts(body, arms).cost(50, 0).recoveryFollowupOf(ATTACK, UNARMED, BARRAGE, DASH, SWEEP).build(), SilverChariotEntity.Kind.DASH);
        register(definition(SWEEP).parts(arms).cost(50, 0).training(1, false, 1)
                .recoveryFollowupOf(ATTACK, UNARMED, BARRAGE, DASH, SWEEP).build(), SilverChariotEntity.Kind.SWEEP);
        register(definition(LAUNCH).hotbar(StandActionRegistry.Hotbar.LEFT, 2).parts(arms).cooldown(0, 100).training(2, false, 1).build(), SilverChariotEntity.Kind.RAPIER_LAUNCH);
        register(definition(BLOCK).hotbar(StandActionRegistry.Hotbar.RIGHT, 0).parts(arms).hold(Integer.MAX_VALUE, 0, false)
                .recoveryFollowupOf(ATTACK, UNARMED).build(), SilverChariotEntity.Kind.BLOCK);
        register(definition(ARMOR).hotbar(StandActionRegistry.Hotbar.RIGHT, 1).parts(body).training(3, false, 1).build(), SilverChariotEntity.Kind.REMOVE_ARMOR);
    }
    public static Identifier replace(ServerPlayer player, Identifier action) {
        var current = StandService.currentManifestation(player);
        if (!(current instanceof SilverChariotEntity stand)) return action;
        if (action.equals(ATTACK) && !stand.hasRapier()) return UNARMED;
        if (action.equals(DASH) && StandParts.has(player, StandParts.Part.ARMS)
                && (!StandParts.has(player, StandParts.Part.MAIN_BODY)
                || ProgressionService.unlocked(player, 1) && (stand.finisherCharge() >= .5F || stand.attackKind() == SilverChariotEntity.Kind.SWEEP))) return SWEEP;
        return action;
    }
    private static void register(StandActionRegistry.Definition definition, SilverChariotEntity.Kind kind) {
        StandActionRegistry.register(definition, new StandActionRegistry.Handler() {
            @Override public boolean ownsTaskLifecycle() { return true; }
            @Override public boolean autoSummons() { return true; }
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context context) {
                return kind != SilverChariotEntity.Kind.UNARMED;
            }
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context context) {
                var preparation = StandService.prepareSummon(context.player());
                if (preparation.manifestation() instanceof SilverChariotEntity stand)
                    stand.prepareActionSummon(kind, context.manifestation() == null);
                return preparation;
            }
            @Override public boolean canReplaceHeld(StandActionRegistry.Context context, Identifier activeId) {
                return (kind == SilverChariotEntity.Kind.DASH || kind == SilverChariotEntity.Kind.SWEEP) && activeId.equals(BARRAGE);
            }
            @Override public boolean canUse(StandActionRegistry.Context context) {
                return context.manifestation() instanceof SilverChariotEntity stand && stand.canUse(kind);
            }
            @Override public boolean execute(StandActionRegistry.Context context) { return ((SilverChariotEntity) context.manifestation()).start(kind); }
            @Override public void keep(StandActionRegistry.Context context) { context.manifestation().refreshHeld(); }
            @Override public boolean tick(StandActionRegistry.Context context, int ticks) {
                var stand = (SilverChariotEntity) context.manifestation(); stand.refreshHeld(); return stand.attackKind() == kind;
            }
            @Override public void release(StandActionRegistry.Context context, int ticks, boolean cancelled) { context.manifestation().stopHeld(); }
        });
    }
    private SilverChariotActions() { }
}
