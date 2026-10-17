package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.mixin.restoration.CreeperPowerAccess;
import com.github.standobyte.jojo.fabric.mixin.restoration.TntMinecartAccess;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Source repair/previous-state/terrain actions. Shared registry owns authority, holds and .2/tick costs. */
public final class CrazyDiamondRestorationActions {
    public static final String STAND_ID = "jojo:crazy_diamond";
    public static final Identifier REPAIR = RipplesFabric.id("crazy_diamond_repair");
    public static final Identifier PREVIOUS_STATE = RipplesFabric.id("crazy_diamond_previous_state");
    public static final Identifier TERRAIN = RipplesFabric.id("crazy_diamond_restore_terrain");
    private static final Identifier ANGELO_EFFECT = RipplesFabric.id("turn_into_angelo_rock");
    private static final Map<ServerPlayer, CrazyDiamondHealing.Session> SESSIONS = new WeakHashMap<>();
    private static final Map<Item, Integer> TOOL_TIERS = new IdentityHashMap<>();
    private static boolean initialized;
    private static boolean useOtherPlayersInventories;

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        CrazyDiamondHealing.initialize();
        RestorationSelection.initialize();
        registerTools(0, Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_HOE, Items.WOODEN_SHOVEL,
                Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_HOE, Items.GOLDEN_SHOVEL);
        registerTools(1, Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_HOE, Items.STONE_SHOVEL);
        registerTools(2, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_HOE, Items.IRON_SHOVEL, SledgehammerItem.SLEDGEHAMMER);
        registerTools(3, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.DIAMOND_SHOVEL);
        registerTools(4, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_HOE, Items.NETHERITE_SHOVEL);
        var arms = StandParts.Part.ARMS;
        register(StandActionRegistry.Definition.builder(REPAIR, STAND_ID).hotbar(StandActionRegistry.Hotbar.RIGHT, 1)
                .parts(arms).shift(PREVIOUS_STATE).hold(Integer.MAX_VALUE, 0, false).cost(0, .2F).training(0, true, 1).build(), RestorationSelection.Mode.REPAIR);
        register(StandActionRegistry.Definition.builder(PREVIOUS_STATE, STAND_ID).parts(arms)
                .hold(Integer.MAX_VALUE, 0, false).cost(0, .2F).training(-1, false, 1).build(), RestorationSelection.Mode.PREVIOUS_STATE);
        register(StandActionRegistry.Definition.builder(TERRAIN, STAND_ID).hotbar(StandActionRegistry.Hotbar.RIGHT, 3)
                .parts(arms).hold(Integer.MAX_VALUE, 0, false).training(2, false, 1).cost(0, 0).build(), RestorationSelection.Mode.TERRAIN);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    private static void registerTools(int tier, Item... items) { for (var item : items) TOOL_TIERS.put(item, tier); }
    /** Modern Item no longer stores TieredItem's mining level; adapters can register additional original tiered items. */
    public static void registerToolTier(Item item, int tier) {
        if (tier < 0) throw new IllegalArgumentException("Negative tool tier");
        TOOL_TIERS.put(item, tier);
    }
    public static void setUseOtherPlayersInventories(boolean enabled) { useOtherPlayersInventories = enabled; }
    public static boolean useOtherPlayersInventories() { return useOtherPlayersInventories; }

    private static void register(StandActionRegistry.Definition definition, RestorationSelection.Mode mode) {
        StandActionRegistry.register(definition, new StandActionRegistry.Handler() {
            @Override public boolean autoSummons() { return true; }
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context context) {
                var preparation = StandService.prepareSummon(context.player());
                if (preparation.manifestation() instanceof RestorationSelection.Stand stand)
                    stand.prepareRestorationSummon(mode, context.manifestation() == null);
                return preparation;
            }
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context context) {
                return mode == RestorationSelection.Mode.REPAIR ? canBeRepaired(context.player().getOffhandItem())
                        : mode == RestorationSelection.Mode.PREVIOUS_STATE
                        ? RecipeReversal.convertTo(context.player().getOffhandItem(), context.player().level(), null, context.player().getRandom(), false).isPresent()
                        : RestorationHooks.getBlocksInRange(context.player().level(), context.player(), RestorationHooks.eyePos(context.player()), 12, block -> true).findAny().isPresent();
            }
            @Override public boolean canUse(StandActionRegistry.Context context) {
                if (!(context.manifestation() instanceof RestorationSelection.Stand stand) || !stand.canUseRestoration(mode)) return false;
                var user = context.player();
                if (mode == RestorationSelection.Mode.REPAIR) return canBeRepaired(user.getOffhandItem());
                if (mode == RestorationSelection.Mode.TERRAIN) return StandEffects.entries(user).stream().noneMatch(effect -> effect.type().equals(ANGELO_EFFECT))
                        && (StandActionRegistry.held(user).filter(TERRAIN::equals).isPresent()
                        || RestorationSelection.terrain(user, context.manifestation()).findAny().isPresent());
                var target = RestorationSelection.target(user, context.manifestation());
                if (target.type() == HitResult.Type.BLOCK) return false;
                if (target.entity() != null) return entityReversible(target.entity(), StandAttachments.resolve(user).level())
                        && (!(target.entity() instanceof LivingEntity living) || context.manifestation().entity().canAttack(living));
                return RecipeReversal.convertTo(user.getOffhandItem(), user.level(), null, user.getRandom(), false).isPresent();
            }
            @Override public boolean execute(StandActionRegistry.Context context) {
                ((RestorationSelection.Stand) context.manifestation()).beginRestoration();
                SESSIONS.put(context.player(), new CrazyDiamondHealing.Session());
                context.manifestation().playOriginal("crazy_diamond_fix_started");
                RestorationSelection.publish(context.manifestation().entity(), mode, 0, null, false, List.of());
                return true;
            }
            @Override public boolean tick(StandActionRegistry.Context context, int ticks) {
                return performTick(context.player(), (RestorationSelection.Stand) context.manifestation(), mode, ticks - 1);
            }
            @Override public void release(StandActionRegistry.Context context, int ticks, boolean cancelled) {
                SESSIONS.remove(context.player());
                RestorationSelection.clear(context.manifestation().entity());
                context.manifestation().playOriginal("crazy_diamond_fix_ended");
            }
        });
    }

    public static double restorationSpeed(RestorationSelection.Stand stand) {
        return stand.restorationAttackSpeed() * .05F + .55;
    }

    public static boolean performTick(ServerPlayer user, RestorationSelection.Stand stand, RestorationSelection.Mode mode, int taskTicks) {
        if (stand.owner() != user || stand.entity().level() != user.level() || !STAND_ID.equals(stand.definition().id())) return false;
        double speed = restorationSpeed(stand);
        if (mode == RestorationSelection.Mode.REPAIR) {
            float points = repairTick(user, user.getOffhandItem(), taskTicks, speed);
            if (points > 0) {
                StandActionTraining.add(user, REPAIR, points, 1, 1);
                unlockPreviousState(user);
            }
            RestorationSelection.publish(stand.entity(), mode, taskTicks, null, true, List.of());
        } else if (mode == RestorationSelection.Mode.PREVIOUS_STATE) {
            var target = RestorationSelection.target(user, stand);
            if (target.type() == HitResult.Type.BLOCK) return false;
            boolean worked = false;
            if (target.entity() != null) {
                if (!entityReversible(target.entity(), StandAttachments.resolve(user).level())) return false;
                worked = reverseEntity(user.level(), target.entity(), stand, taskTicks,
                        SESSIONS.computeIfAbsent(user, ignored -> new CrazyDiamondHealing.Session()));
            } else {
                var held = user.getOffhandItem();
                if (repairTick(user, held, taskTicks, speed) == 0 && itemTransformationTick(taskTicks, speed)) {
                    dropExperience(user, held);
                    RecipeReversal.convertTo(held, user.level(), null, stand.entity().getRandom(), true).ifPresent(result -> {
                        boolean gave = false;
                        for (var ingredient : result.ingredients()) if (!ingredient.isEmpty()) { giveItem(user, ingredient); gave = true; }
                        if (gave) held.shrink(result.consumedCount());
                    });
                }
            }
            RestorationSelection.publish(stand.entity(), mode, taskTicks, target.entity(), worked, List.of());
        } else if (mode == RestorationSelection.Mode.TERRAIN) {
            var camera = RestorationSelection.camera(user, stand);
            var eye = RestorationHooks.eyePos(camera);
            boolean resolve = StandAttachments.resolve(user).isBurst();
            double rate = speed * 3;
            int blocks = (int) rate + (stand.entity().getRandom().nextDouble() < rate % 1 ? 1 : 0);
            int limit = resolve ? 64 : Math.min(blocks, (int) (2 * user.getAttachedOrCreate(StandAttachments.PROGRESS).stamina()));
            var area = camera.getBoundingBox().inflate(24);
            var materials = RestorationHooks.sourceItemStacks(area, area.getCenter(), user, user.level(), useOtherPlayersInventories);
            var result = RestorationHooks.restoreBlocks(user.level(), stand.entity(), RestorationSelection.terrain(user, stand),
                    Comparator.comparingInt(block -> block.pos.distManhattan(eye)), limit,
                    user.isCreative(), resolve && !user.isShiftKeyDown(), true, user, materials);
            if (!StandConfig.staminaFree(user) && !result.blocksPlaced.isEmpty()) {
                var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS);
                user.setAttached(StandAttachments.PROGRESS, progress.withStamina(Math.max(0, progress.stamina() - 2 * result.blocksPlaced.size())));
            }
            RestorationSelection.publish(stand.entity(), mode, taskTicks, null, !result.blocksPlaced.isEmpty(), List.copyOf(result.blocksPlaced));
        }
        return true;
    }

    public static void unlockPreviousState(ServerPlayer player) {
        if (StandActionTraining.points(player, REPAIR, 1) >= 1) StandActionTraining.unlock(player, PREVIOUS_STATE, false, 1);
    }

    public static boolean itemTransformationTick(int ticks, double speed) {
        int interval = (int) (10 / speed);
        return ticks % interval == interval - 1;
    }

    public static boolean canBeRepaired(ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamaged() || stack.isEnchanted() || replacement(stack) != null);
    }
    private static Item replacement(ItemStack stack) {
        if (stack.is(Items.CHIPPED_ANVIL)) return Items.ANVIL;
        if (stack.is(Items.DAMAGED_ANVIL)) return Items.CHIPPED_ANVIL;
        if (stack.is(Items.COBBLESTONE)) return Items.STONE;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getPath().contains("cracked")) return null;
        var replacement = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("cracked_", ""));
        return BuiltInRegistries.ITEM.containsKey(replacement) ? BuiltInRegistries.ITEM.getValue(replacement) : null;
    }

    public static float repairTick(LivingEntity user, ItemStack stack, int ticks, double speed) {
        int damage = 0;
        Item replacement = replacement(stack);
        if (replacement != null) damage = stack.is(Items.CHIPPED_ANVIL) || stack.is(Items.DAMAGED_ANVIL) ? 125 : 1;
        if (replacement != null && user instanceof Player player) {
            if (itemTransformationTick(ticks, speed)) {
                user.setItemInHand(InteractionHand.OFF_HAND, ItemUtils.createFilledResult(stack, player, new ItemStack(replacement), false));
                if (player.getAbilities().instabuild) stack.shrink(1);
            } else damage = -1;
        }
        float multiplier = 1;
        if (!stack.isEmpty()) {
            multiplier += switch (stack.getRarity()) { case UNCOMMON -> .5F; case RARE -> 1.5F; case EPIC -> 3; default -> 0; };
            multiplier += TOOL_TIERS.getOrDefault(stack.getItem(), 0) / 2F;
            dropExperience(user, stack);
            stack.remove(DataComponents.ENCHANTMENTS); stack.remove(DataComponents.STORED_ENCHANTMENTS);
            int restored = Math.min(stack.getDamageValue(), (int) (speed * 40));
            damage += restored;
            if (stack.isDamageableItem()) {
                stack.setDamageValue(stack.getDamageValue() - restored);
                stack.set(DataComponents.REPAIR_COST, 0);
            }
        }
        return damage * multiplier * (.002F / 13F);
    }

    public static void dropExperience(LivingEntity user, ItemStack stack) {
        if (!(user.level() instanceof ServerLevel level) || !(stack.hasFoil() || stack.isEnchanted())) return;
        int base = 0;
        for (var enchantment : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            if (!enchantment.getKey().is(EnchantmentTags.CURSE)) base += enchantment.getKey().value().getMinCost(enchantment.getIntValue());
        }
        if (base <= 0) return;
        int half = (int) Math.ceil(base / 2D);
        int xp = half + level.random.nextInt(half);
        var offset = new Vec3(user.getBbWidth() * .6 * (user.getMainArm() == HumanoidArm.LEFT ? -1 : 1),
                user.getBbHeight() * (user.isShiftKeyDown() ? .25 : .45), user.getBbWidth() * .7).yRot(-user.yBodyRot * ((float) Math.PI / 180));
        var pos = user.position().add(offset);
        while (xp > 0) {
            int amount = ExperienceOrb.getExperienceValue(xp); xp -= amount;
            level.addFreshEntity(new ExperienceOrb(level, pos.x, pos.y, pos.z, amount));
        }
    }

    public static void giveItem(LivingEntity user, ItemStack stack) {
        if (user instanceof Player player && player.getInventory().add(stack) && stack.isEmpty()) return;
        if (stack.isEmpty()) return;
        var drop = new ItemEntity(user.level(), user.getX(), user.getEyeY() - .3, user.getZ(), stack);
        drop.setNoPickUpDelay(); drop.setTarget(user.getUUID()); user.level().addFreshEntity(drop);
    }

    public static boolean entityReversible(Entity entity, int resolve) {
        if (resolve < 3 || !entity.isAlive()) return false;
        return entity instanceof PrimedTnt || entity instanceof MinecartTNT || entity.getType() == EntityTypes.SNOW_GOLEM
                || entity instanceof Creeper creeper && creeper.isPowered()
                || resolve >= 4 && (entity.getType() == EntityTypes.IRON_GOLEM
                || entity.getType() == EntityTypes.WITHER && ((WitherBoss) entity).getInvulnerableTicks() > 0);
    }

    private static boolean reverseEntity(ServerLevel level, Entity target, RestorationSelection.Stand stand, int ticks,
            CrazyDiamondHealing.Session session) {
        var random = stand.entity().getRandom();
        double speed = restorationSpeed(stand);
        if (target instanceof PrimedTnt tnt) {
            if (ticks == 0 || tnt.getFuse() < 80) tnt.setFuse(tnt.getFuse() + 2);
            else {
                var block = BuiltInRegistries.BLOCK.getValue(BuiltInRegistries.ENTITY_TYPE.getKey(tnt.getType()));
                if (block == null || block == Blocks.AIR) block = Blocks.TNT;
                var pos = tnt.blockPosition(); tnt.discard();
                if (!tnt.isAlive()) replaceOrDropBlock(level, pos, block.defaultBlockState());
            }
            return true;
        }
        if (target instanceof MinecartTNT minecart) {
            int fuse = minecart.getFuse();
            if (fuse >= 80) ((TntMinecartAccess) minecart).jojo$setRestorationFuse(-1);
            else if (fuse > -1) ((TntMinecartAccess) minecart).jojo$setRestorationFuse(fuse + 2);
            return fuse > -1;
        }
        if (target instanceof Creeper creeper) {
            if (creeper.isPowered() && !CrazyDiamondHealing.healLivingEntity(creeper, speed, session) && random.nextFloat() < .05F)
                creeper.getEntityData().set(CreeperPowerAccess.jojo$restorationPoweredData(), false);
            return true;
        }
        if (target.getType() == EntityTypes.SNOW_GOLEM || target.getType() == EntityTypes.IRON_GOLEM) {
            boolean snow = target.getType() == EntityTypes.SNOW_GOLEM;
            if (!CrazyDiamondHealing.healLivingEntity((LivingEntity) target, speed, session) && random.nextFloat() < (snow ? .1F : .05F)) {
                var pos = target.blockPosition(); target.discard();
                if (!target.isAlive()) {
                    var material = snow ? Blocks.SNOW_BLOCK : Blocks.IRON_BLOCK;
                    if (!snow) replaceOrDropBlock(level, pos, material.defaultBlockState());
                    replaceOrDropBlock(level, pos.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState());
                    if (snow) replaceOrDropBlock(level, pos, material.defaultBlockState());
                    replaceOrDropBlock(level, pos.above(), material.defaultBlockState());
                    if (!snow) {
                        replaceOrDropBlock(level, pos.offset(1, 1, 0), material.defaultBlockState());
                        replaceOrDropBlock(level, pos.offset(-1, 1, 0), material.defaultBlockState());
                    }
                }
            }
            return true;
        }
        if (target instanceof WitherBoss wither) {
            int spawnTicks = wither.getInvulnerableTicks();
            if (spawnTicks > 0) {
                wither.setInvulnerableTicks(Math.min(spawnTicks + 5, 220));
                if (spawnTicks >= 215 && random.nextFloat() < .005F) {
                    var pos = wither.blockPosition(); wither.discard();
                    if (!wither.isAlive()) {
                        replaceOrDropBlock(level, pos.above(2), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
                        replaceOrDropBlock(level, pos.offset(1, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
                        replaceOrDropBlock(level, pos.offset(-1, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
                        replaceOrDropBlock(level, pos, Blocks.SOUL_SAND.defaultBlockState());
                        replaceOrDropBlock(level, pos.above(), Blocks.SOUL_SAND.defaultBlockState());
                        replaceOrDropBlock(level, pos.offset(1, 1, 0), Blocks.SOUL_SAND.defaultBlockState());
                        replaceOrDropBlock(level, pos.offset(-1, 1, 0), Blocks.SOUL_SAND.defaultBlockState());
                    }
                }
            }
            return true;
        }
        return false;
    }

    public static boolean canReplaceBlock(ServerLevel level, BlockPos pos, BlockState replacement) {
        var current = level.getBlockState(pos);
        float hardness = current.getDestroySpeed(level, pos);
        return current.canBeReplaced() || hardness >= 0 && hardness < replacement.getDestroySpeed(level, pos);
    }
    private static void replaceOrDropBlock(ServerLevel level, BlockPos pos, BlockState replacement) {
        if (canReplaceBlock(level, pos, replacement)) {
            level.destroyBlock(pos, true); level.setBlockAndUpdate(pos, replacement);
        } else if (replacement.getBlock().asItem() != Items.AIR) {
            var center = Vec3.atCenterOf(pos);
            level.addFreshEntity(new ItemEntity(level, center.x, center.y, center.z, new ItemStack(replacement.getBlock())));
        }
    }
    private CrazyDiamondRestorationActions() { }
}
