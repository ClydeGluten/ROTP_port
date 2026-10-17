package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** Original four attack slots, five ability slots, shift variations and recovery replacements. */
public final class CrazyDiamondCombatActions {
    public static final String ID = CrazyDiamondRestorationActions.STAND_ID;
    public static final EntityType<CrazyDiamondEntity> STAND = registerEntity("crazy_diamond", CrazyDiamondEntity::new, .65F, 1.95F, true);
    public static final EntityType<CDBlockBulletEntity> BLOCK_BULLET_ENTITY = registerEntity("cd_block_bullet", CDBlockBulletEntity::new, .5F, .5F, false);
    public static final EntityType<CDBloodCutterEntity> BLOOD_CUTTER_ENTITY = registerEntity("cd_blood_cutter", CDBloodCutterEntity::new, .5F, .5F, false);
    public static final EntityType<AngeloRockEntity> ANGELO_ENTITY = registerEntity("angelo_rock", AngeloRockEntity::new, 1, 1.75F, false);
    public static final EntityType<CDRecoveryModifiers.EyeFlight> EYE_INSIDE = registerEntity("eye_of_ender_inside", CDRecoveryModifiers.EyeFlight::new, .25F, .25F, false);
    public static final EntityType<CDRecoveryModifiers.FireworkFlight> FIREWORK_INSIDE = registerEntity("firework_inside", CDRecoveryModifiers.FireworkFlight::new, .25F, .25F, false);
    public static final Identifier PUNCH = id("punch"), BARRAGE = id("barrage"), HEAVY = id("heavy_punch"), FINISHER = id("misshaping_punch"),
            BLOCK = id("block"), BLOCK_BULLET = id("block_bullet"), BLOOD_CUTTER = id("blood_cutter"), HEAL = id("heal"),
            ANCHOR_MOVE = id("anchor_move"), ANCHOR_MAKE = id("anchor_make"), LEAVE_OBJECT = id("leave_object"),
            MISSHAPE_FACE = id("misshape_face"), MISSHAPE_ARMS = id("misshape_arms"), MISSHAPE_LEGS = id("misshape_legs"), ANGELO = id("angelo_rock");
    private static final Map<ServerPlayer, CrazyDiamondHealing.Session> HEALING = new WeakHashMap<>();
    private static boolean initialized;
    private static Identifier id(String suffix) { return RipplesFabric.id("crazy_diamond_" + suffix); }
    private static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.EntityFactory<T> factory, float width, float height, boolean noSave) {
        var name = RipplesFabric.id(id); var builder = EntityType.Builder.of(factory, MobCategory.MISC).sized(width, height).noSummon().clientTrackingRange(10).updateInterval(1);
        if (noSave) builder.noSave();
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, name, builder.build(ResourceKey.create(Registries.ENTITY_TYPE, name)));
    }
    private static StandActionRegistry.Builder definition(Identifier action) { return StandActionRegistry.Definition.builder(action, ID); }
    public static void initialize() {
        if (initialized) return; initialized = true;
        CrazyDiamondRestorationActions.initialize(); CDAnchorItem.initialize(); CDRecoveryModifiers.initialize();
        var arms = StandParts.Part.ARMS;
        StandActionRegistry.registerReplacement(ID, CrazyDiamondCombatActions::replace);
        combat(definition(PUNCH).parts(arms).hotbar(StandActionRegistry.Hotbar.LEFT, 0).shift(HEAVY).cost(10, 0).build(), CrazyDiamondEntity.Kind.LIGHT);
        combat(definition(BARRAGE).parts(arms).hotbar(StandActionRegistry.Hotbar.LEFT, 1).shift(HEAVY).hold(Integer.MAX_VALUE, 0, false).cost(0, 4).build(), CrazyDiamondEntity.Kind.BARRAGE);
        combat(definition(HEAVY).parts(arms).cost(50, 0).build(), CrazyDiamondEntity.Kind.HEAVY);
        combat(definition(FINISHER).parts(arms).cost(50, 0).training(1, false, 1).build(), CrazyDiamondEntity.Kind.FINISHER);
        combat(definition(BLOCK_BULLET).parts(arms).hotbar(StandActionRegistry.Hotbar.LEFT, 2).cost(40, 0).training(4, false, 1).build(), CrazyDiamondEntity.Kind.BLOCK_BULLET);
        combat(definition(BLOOD_CUTTER).parts(arms).hotbar(StandActionRegistry.Hotbar.LEFT, 3).cost(25, 0).cooldown(0, 300).training(4, false, 1).build(), CrazyDiamondEntity.Kind.BLOOD_CUTTER);
        combat(definition(BLOCK).parts(arms).hotbar(StandActionRegistry.Hotbar.RIGHT, 0).hold(Integer.MAX_VALUE, 0, false).build(), CrazyDiamondEntity.Kind.BLOCK);
        held(definition(HEAL).parts(arms).hotbar(StandActionRegistry.Hotbar.RIGHT, 2).training(1, false, 1).cost(0, 1).hold(Integer.MAX_VALUE, 0, false).build(), RestorationSelection.Mode.HEAL);
        held(definition(ANCHOR_MOVE).parts(arms).hotbar(StandActionRegistry.Hotbar.RIGHT, 4).shift(ANCHOR_MAKE).training(3, false, 1).cost(0, 1).hold(Integer.MAX_VALUE, 0, false).build(), RestorationSelection.Mode.ANCHOR);
        combat(definition(ANCHOR_MAKE).parts(arms).training(3, false, 1).cost(25, 0).build(), CrazyDiamondEntity.Kind.ANCHOR_MAKE);
        modifier(LEAVE_OBJECT, CDRecoveryModifiers.Modifier.LEAVE_OBJECT, 1, HEAVY);
        modifier(MISSHAPE_FACE, CDRecoveryModifiers.Modifier.FACE, 1, FINISHER);
        modifier(MISSHAPE_ARMS, CDRecoveryModifiers.Modifier.ARMS, 1, FINISHER);
        modifier(MISSHAPE_LEGS, CDRecoveryModifiers.Modifier.LEGS, 1, FINISHER);
        modifier(ANGELO, CDRecoveryModifiers.Modifier.ANGELO, 2, FINISHER);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> HEALING.clear());
    }
    private static Identifier replace(ServerPlayer player, Identifier action) {
        if (!(StandService.currentManifestation(player) instanceof CrazyDiamondEntity stand)) return action;
        if (action.equals(HEAVY) && (stand.finisherCharge() >= .5F || stand.attackKind() == CrazyDiamondEntity.Kind.FINISHER) && ProgressionService.unlocked(player, 1)) action = FINISHER;
        if (action.equals(HEAVY) && CDRecoveryModifiers.canAttach(stand, CDRecoveryModifiers.Modifier.LEAVE_OBJECT)) return LEAVE_OBJECT;
        if (action.equals(FINISHER) && stand.hitPart() != null) {
            var modifier = switch (stand.hitPart()) { case HEAD -> CDRecoveryModifiers.Modifier.FACE; case TORSO_ARMS -> CDRecoveryModifiers.Modifier.ARMS; case LEGS -> CDRecoveryModifiers.Modifier.LEGS; };
            if (CDRecoveryModifiers.canAttach(stand, modifier)) return modifier == CDRecoveryModifiers.Modifier.FACE ? MISSHAPE_FACE : modifier == CDRecoveryModifiers.Modifier.ARMS ? MISSHAPE_ARMS : MISSHAPE_LEGS;
        }
        if (action.equals(CrazyDiamondRestorationActions.TERRAIN) && CDRecoveryModifiers.canAttach(stand, CDRecoveryModifiers.Modifier.ANGELO)) return ANGELO;
        return action;
    }
    private static void combat(StandActionRegistry.Definition definition, CrazyDiamondEntity.Kind kind) {
        StandActionRegistry.register(definition, new StandActionRegistry.Handler() {
            @Override public boolean ownsTaskLifecycle() { return true; }
            @Override public boolean autoSummons() { return true; }
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context context) { return true; }
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context context) {
                var preparation = StandService.prepareSummon(context.player());
                if (preparation.manifestation() instanceof CrazyDiamondEntity stand) stand.prepareActionSummon(kind, context.manifestation() == null);
                return preparation;
            }
            @Override public boolean canReplaceHeld(StandActionRegistry.Context context, Identifier active) { return (kind == CrazyDiamondEntity.Kind.HEAVY || kind == CrazyDiamondEntity.Kind.FINISHER) && active.equals(BARRAGE); }
            @Override public boolean canUse(StandActionRegistry.Context context) {
                if (!(context.manifestation() instanceof CrazyDiamondEntity stand) || !stand.canUse(kind)) return false;
                if (kind == CrazyDiamondEntity.Kind.BLOCK_BULLET) return blockBulletEligible(context.player().getOffhandItem(), stand);
                if (kind == CrazyDiamondEntity.Kind.BLOOD_CUTTER) return context.player().getHealth() < context.player().getMaxHealth() || context.player().getAbilities().invulnerable;
                if (kind == CrazyDiamondEntity.Kind.ANCHOR_MAKE) {
                    var player = context.player(); var start = player.getEyePosition();
                    var hit = player.level().clip(new net.minecraft.world.level.ClipContext(start, start.add(player.getLookAngle().scale(player.blockInteractionRange())), net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                    return hit.getType() == HitResult.Type.BLOCK && CDAnchorItem.breakable(player, hit.getBlockPos(), stand.attackDamage());
                }
                return true;
            }
            @Override public boolean execute(StandActionRegistry.Context context) { return ((CrazyDiamondEntity) context.manifestation()).start(kind); }
            @Override public void keep(StandActionRegistry.Context context) { context.manifestation().refreshHeld(); }
            @Override public boolean tick(StandActionRegistry.Context context, int ticks) { var stand = (CrazyDiamondEntity) context.manifestation(); stand.refreshHeld(); return stand.attackKind() == kind; }
            @Override public void release(StandActionRegistry.Context context, int ticks, boolean cancelled) { context.manifestation().stopHeld(); }
        });
    }
    private static void held(StandActionRegistry.Definition definition, RestorationSelection.Mode mode) {
        StandActionRegistry.register(definition, new StandActionRegistry.Handler() {
            @Override public boolean autoSummons() { return true; }
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context context) { return mode == RestorationSelection.Mode.HEAL || !CDAnchorItem.selected(context.player()).isEmpty(); }
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context context) {
                var preparation = StandService.prepareSummon(context.player());
                if (preparation.manifestation() instanceof CrazyDiamondEntity stand) stand.prepareRestorationSummon(mode, context.manifestation() == null);
                return preparation;
            }
            @Override public boolean canUse(StandActionRegistry.Context context) {
                if (!(context.manifestation() instanceof CrazyDiamondEntity stand) || !stand.canUseRestoration(mode)) return false;
                var target = RestorationSelection.target(context.player(), stand, true).entity();
                return mode == RestorationSelection.Mode.HEAL ? target != null && CrazyDiamondHealing.validTarget(target, context.player()) : !CDAnchorItem.selected(context.player()).isEmpty();
            }
            @Override public boolean execute(StandActionRegistry.Context context) {
                var stand = (CrazyDiamondEntity) context.manifestation(); stand.beginRestoration(); HEALING.put(context.player(), new CrazyDiamondHealing.Session());
                stand.playOriginal("crazy_diamond_fix_started"); RestorationSelection.publish(stand, mode, 0, null, false, List.of()); return true;
            }
            @Override public boolean tick(StandActionRegistry.Context context, int ticks) {
                var stand = (CrazyDiamondEntity) context.manifestation(); var user = context.player();
                if (mode == RestorationSelection.Mode.HEAL) {
                    var target = RestorationSelection.target(user, stand, true).entity(); if (target == null) return false;
                    boolean healed = CrazyDiamondHealing.heal(target, CrazyDiamondRestorationActions.restorationSpeed(stand), HEALING.computeIfAbsent(user, ignored -> new CrazyDiamondHealing.Session()));
                    RestorationSelection.publish(stand, mode, ticks - 1, target, healed, List.of());
                } else {
                    var restored = CDAnchorItem.move(user); restored.ifPresent(pos -> stand.playOriginal("crazy_diamond_fix_ended"));
                    RestorationSelection.publish(stand, mode, ticks - 1, null, restored.isPresent(), restored.stream().toList());
                }
                return true;
            }
            @Override public void release(StandActionRegistry.Context context, int ticks, boolean cancelled) { HEALING.remove(context.player()); RestorationSelection.clear(context.manifestation().entity()); }
        });
    }
    private static void modifier(Identifier id, CDRecoveryModifiers.Modifier modifier, int resolve, Identifier parent) {
        StandActionRegistry.register(definition(id).cost(50, 0).training(resolve, false, 1).recoveryFollowupOf(parent).build(), new StandActionRegistry.Handler() {
            @Override public boolean canUse(StandActionRegistry.Context context) { return context.manifestation() instanceof CrazyDiamondEntity stand && CDRecoveryModifiers.canAttach(stand, modifier); }
            @Override public boolean execute(StandActionRegistry.Context context) { return ((CrazyDiamondEntity) context.manifestation()).attachModifier(modifier); }
        });
    }
    public static boolean blockBulletEligible(ItemStack stack, CrazyDiamondEntity stand) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
        var state = item.getBlock().defaultBlockState(); float hardness = state.getDestroySpeed(stand.level(), stand.owner().blockPosition());
        int harvest = state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL) ? 2 : state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
        return CDRecoveryModifiers.hardMaterial(state) && com.github.standobyte.jojo.core.StandStatFormulas.isBlockBreakable(stand.attackDamage(), hardness, harvest);
    }
    public static void fireBlockBullet(CrazyDiamondEntity stand) {
        var user = stand.owner(); var stack = user.getOffhandItem(); if (!(stack.getItem() instanceof BlockItem item)) return;
        var bullet = new CDBlockBulletEntity(stand, item.getBlock()); var look = stand.getLookAngle(); bullet.shoot(look.x, look.y, look.z, 2, stand.projectileInaccuracy(.25F));
        stand.level().addFreshEntity(bullet); if (!user.isCreative()) stack.shrink(1);
        if (!user.isShiftKeyDown()) StandEffectStore.getTargetLookedAt(user, CDRecoveryModifiers.DRIED_BLOOD, 64).ifPresent(effect -> bullet.setTarget(StandEffectStore.target(user, effect)));
        stand.playOriginal("crazy_diamond_bullet_shot");
    }
    public static void fireBloodCutter(CrazyDiamondEntity stand) {
        var user = stand.owner(); var cutter = new CDBloodCutterEntity(stand); var look = user.getLookAngle(); cutter.shoot(look.x, look.y, look.z, 2, stand.projectileInaccuracy(1));
        stand.level().addFreshEntity(cutter); stand.playOriginal("crazy_diamond_blood_cutter_shot");
        if (!user.isCreative()) AbilityCooldowns.start(user, BLOOD_CUTTER, (int) Math.ceil(300F * user.getHealth() / user.getMaxHealth()));
    }
    private CrazyDiamondCombatActions() { }
}
