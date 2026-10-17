package com.github.standobyte.jojo.fabric.stand.hierophant;

import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ShieldItem;
import static com.github.standobyte.jojo.fabric.StandActionRegistry.Hotbar.*;

/** Original eight HG definitions, with entity tasks owning perform-time resource use. */
public final class HierophantGreenActions {
    public static final String STAND_ID = "jojo:hierophant_green";
    public static final Identifier ATTACK = RipplesFabric.id("hierophant_green_attack");
    public static final Identifier BIND = RipplesFabric.id("hierophant_green_attack_binding");
    public static final Identifier SPLASH = RipplesFabric.id("hierophant_green_emerald_splash");
    public static final Identifier CONCENTRATED = RipplesFabric.id("hierophant_green_es_concentrated");
    public static final Identifier BLOCK = RipplesFabric.id("hierophant_green_block");
    public static final Identifier GRAPPLE = RipplesFabric.id("hierophant_green_grapple");
    public static final Identifier GRAPPLE_ENTITY = RipplesFabric.id("hierophant_green_grapple_entity");
    public static final Identifier BARRIER = RipplesFabric.id("hierophant_green_barrier");
    public static final EntityType<HierophantGreenEntity> TYPE = entity("hierophant_green",
            EntityType.Builder.<HierophantGreenEntity>of(HierophantGreenEntity::new, MobCategory.MISC)
                    .sized(.6F, 1.9F).eyeHeight(1.615F).clientTrackingRange(8).updateInterval(2).noSave().noSummon());
    public static final EntityType<HGStringEntity> STRING = entity("hg_string",
            EntityType.Builder.<HGStringEntity>of(HGStringEntity::new, MobCategory.MISC)
                    .sized(.25F, .25F).updateInterval(20).noSave().noSummon());
    public static final EntityType<HGEmeraldEntity> EMERALD = entity("hg_emerald",
            EntityType.Builder.<HGEmeraldEntity>of(HGEmeraldEntity::new, MobCategory.MISC)
                    .sized(.5F, .25F).updateInterval(10).noSave().noSummon());
    public static final EntityType<HGGrapplingStringEntity> GRAPPLING_STRING = entity("hg_grappling_string",
            EntityType.Builder.<HGGrapplingStringEntity>of(HGGrapplingStringEntity::new, MobCategory.MISC)
                    .sized(.25F, .25F).updateInterval(20).noSave().noSummon());
    public static final EntityType<HGBarrierEntity> BARRIER_ENTITY = entity("hg_barrier",
            EntityType.Builder.<HGBarrierEntity>of(HGBarrierEntity::new, MobCategory.MISC)
                    .sized(.25F, .25F).noUpdateInterval().dontTrackDeltas().noSave().noSummon());
    private static boolean initialized;
    private static <T extends Entity> EntityType<T> entity(String path, EntityType.Builder<T> builder) {
        var key = ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id(path));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
    public static void initialize() {
        if (initialized) return; initialized = true;
        FabricDefaultAttributeRegistry.register(TYPE, Mob.createMobAttributes());
        StandService.registerFactory(STAND_ID, level -> new HierophantGreenEntity(TYPE, level));
        register(base(ATTACK).hotbar(LEFT, 0).shift(BIND).parts(StandParts.Part.MAIN_BODY).cost(75, 0).movement(.5F).build());
        register(base(BIND).parts(StandParts.Part.MAIN_BODY).cost(75, 0).cooldown(25, 100).resolveCooldownMultiplier(.5F).movement(.5F).build());
        register(base(SPLASH).hotbar(LEFT, 1).shift(CONCENTRATED).parts(StandParts.Part.ARMS).cost(0, 3).training(1, true, 1).movement(.5F).build());
        register(base(CONCENTRATED).parts(StandParts.Part.ARMS).cost(0, 6).training(-1, false, 1).cooldown(5, 60).movement(.5F).build());
        register(base(BLOCK).hotbar(RIGHT, 0).parts(StandParts.Part.ARMS).hold(Integer.MAX_VALUE, 0, false).movement(.3F).build());
        register(base(GRAPPLE).hotbar(RIGHT, 1).shift(GRAPPLE_ENTITY).parts(StandParts.Part.ARMS).hold(Integer.MAX_VALUE, 0, false)
                .cost(0, 1).training(2, false, 1).movement(0).build());
        register(base(GRAPPLE_ENTITY).parts(StandParts.Part.ARMS).hold(Integer.MAX_VALUE, 0, false)
                .cost(0, 1).training(2, false, 1).movement(0).build());
        register(base(BARRIER).hotbar(RIGHT, 2).parts(StandParts.Part.MAIN_BODY).training(3, false, 1).movement(.5F).build());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) if (player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(STAND_ID)) unlockConcentrated(player);
        });
    }
    private static StandActionRegistry.Builder base(Identifier id) { return StandActionRegistry.Definition.builder(id, STAND_ID); }
    private static void register(StandActionRegistry.Definition definition) {
        StandActionRegistry.register(definition, new StandActionRegistry.Handler() {
            @Override public boolean autoSummons() { return true; }
            @Override public boolean canReplaceHeld(StandActionRegistry.Context context, Identifier activeId) {
                var held = task(activeId);
                return held == HierophantGreenEntity.Task.BLOCK || held == HierophantGreenEntity.Task.GRAPPLE || held == HierophantGreenEntity.Task.GRAPPLE_ENTITY;
            }
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context context) {
                boolean fresh = context.manifestation() == null;
                var preparation = StandService.prepareSummon(context.player());
                if (preparation.manifestation() instanceof HierophantGreenEntity stand) {
                    var next = task(context.definition().id());
                    // Parts required do not select summon mode: only StandEntityBlock overrides
                    // StandEntityAction.Builder's FULL default for this source Stand.
                    boolean arms = next == HierophantGreenEntity.Task.BLOCK;
                    if (arms && (fresh || stand.isArmsOnlyMode())) stand.setArmsOnlyMode();
                    else if (!arms) stand.fullSummonFromArms();
                }
                return preparation;
            }
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context context) {
                if (VampireCombatService.isStunned(context.player())) return false;
                return task(context.definition().id()) != HierophantGreenEntity.Task.BARRIER
                        || context.player().pick(10, 1, false).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
            }
            @Override public boolean ownsTaskLifecycle() { return true; }
            @Override public boolean canUse(StandActionRegistry.Context context) {
                if (!(context.manifestation() instanceof HierophantGreenEntity stand)) return false;
                var next = task(context.definition().id());
                boolean holding = context.definition().held() && stand.task() == next;
                return stand.canStart(next) && (!holding || canPay(context.player(), context.definition().tickCost()));
            }
            @Override public boolean execute(StandActionRegistry.Context context) {
                return context.manifestation() instanceof HierophantGreenEntity stand && stand.begin(context.definition().id());
            }
            @Override public boolean tick(StandActionRegistry.Context context, int ticks) {
                return context.manifestation() instanceof HierophantGreenEntity stand && stand.task() == task(context.definition().id());
            }
            @Override public void keep(StandActionRegistry.Context context) { if (context.manifestation() instanceof HierophantGreenEntity stand) stand.refreshHeld(); }
            @Override public void release(StandActionRegistry.Context context, int ticks, boolean cancelled) {
                if (context.manifestation() instanceof HierophantGreenEntity stand && stand.task() == task(context.definition().id())) stand.stopHeld();
            }
        });
    }
    public static Identifier id(HierophantGreenEntity.Task task) {
        return switch (task) {
            case STRING -> ATTACK; case BIND -> BIND; case SPLASH -> SPLASH; case CONCENTRATED -> CONCENTRATED;
            case BLOCK -> BLOCK; case GRAPPLE -> GRAPPLE; case GRAPPLE_ENTITY -> GRAPPLE_ENTITY; case BARRIER -> BARRIER;
            case NONE -> null;
        };
    }
    public static HierophantGreenEntity.Task task(Identifier id) {
        for (var task : HierophantGreenEntity.Task.values()) if (id != null && id.equals(id(task))) return task;
        return HierophantGreenEntity.Task.NONE;
    }
    public static boolean dispatch(ServerPlayer player, Identifier id) {
        return StandActionRegistry.dispatch(player, id, StandActionRegistry.Phase.PRESS, false, StandActionRegistry.Extra.EMPTY);
    }
    static boolean canPay(ServerPlayer player, float amount) {
        return StandConfig.staminaFree(player) || StandAttachments.resolve(player).isBurst() || player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina() >= amount;
    }
    /** Source consumeStamina spends/clamps even when it returns false. Barrier callers use the result to stop firing. */
    static boolean consumeStamina(ServerPlayer player, float amount) {
        if (StandConfig.staminaFree(player)) return true;
        var progress = player.getAttachedOrCreate(StandAttachments.PROGRESS); boolean enough = canPay(player, amount);
        player.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - amount))); return enough;
    }
    static void startCooldown(ServerPlayer player, Identifier action) {
        var definition = StandActionRegistry.definition(action).orElseThrow();
        int additional = player.isCreative() ? 0 : definition.additionalCooldown();
        if (StandAttachments.resolve(player).isBurst()) additional = (int) (additional * definition.resolveCooldownMultiplier());
        int duration = definition.technicalCooldown() + additional;
        if (duration > 0) AbilityCooldowns.start(player, action, duration);
    }
    public static void trainSplash(ServerPlayer player) {
        StandActionTraining.add(player, SPLASH, .002F, 1, 4); unlockConcentrated(player);
    }
    private static void unlockConcentrated(ServerPlayer player) {
        if (StandActionTraining.points(player, SPLASH, 1) >= 1) StandActionTraining.unlock(player, CONCENTRATED, false, 1);
    }
    public static boolean isBlocking(LivingEntity entity) {
        return entity.getUseItem().getItem() instanceof ShieldItem || entity instanceof StandManifestation stand && stand.actionPhase() == 5;
    }
    static int fractionCount(RandomSource random, double count) {
        if (!Double.isFinite(count) || count <= 0) return 0;
        int full = (int) Math.min(Math.floor(count), 2500);
        return full + (random.nextDouble() < count - Math.floor(count) ? 1 : 0);
    }
    private HierophantGreenActions() { }
}
