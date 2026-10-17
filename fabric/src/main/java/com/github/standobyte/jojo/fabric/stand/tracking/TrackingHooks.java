package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StuckKnives;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.BiConsumer;

/** One root-owned mixin adapter per original transfer path; no feature-specific duplicate trackers. */
public final class TrackingHooks {
    private static final AttachmentType<List<UUID>> REFUSE_TRADE = AttachmentRegistry.<List<UUID>>builder().initializer(List::of)
            .persistent(UUIDUtil.CODEC.listOf(0, 1024)).buildAndRegister(RipplesFabric.id("tracked_item_trade_refusal"));
    private static final class PiglinAccess extends net.minecraft.world.entity.monster.piglin.PiglinAi {
        static void scammed(ServerLevel level, Piglin piglin, LivingEntity thief) { wasHurtBy(level, piglin, thief); }
    }
    public static void entityChanged(Entity entity) { ItemTrackingService.entityChanged(entity); }
    public static void containerChanged(ServerLevel level, BlockPos pos, Container container) { ItemTrackingService.containerChanged(level, pos, container); }
    public static void inventoryChanged(LivingEntity holder, Container inventory) { ItemTrackingService.inventoryChanged(holder, inventory); }
    public static void projectileCreated(Entity projectile, ItemStack source, boolean detachIdentityFromSource) {
        ItemTrackingService.carry(projectile, source, ItemTrackingService.Kind.ENTITY_IS_ITEM, detachIdentityFromSource);
    }
    public static void projectileEmbedded(Entity projectile, LivingEntity target, boolean knife) { ItemTrackingService.embedded(projectile, target, knife); }
    public static ItemStack projectilePickup(Entity projectile, ItemStack pickup) {
        for (var carried : projectile.getAttachedOrCreate(ItemTrackingService.CARRIED)) {
            var identity = TrackedItemIdentity.get(carried.stack());
            if (identity.isPresent()) { pickup.set(TrackedItemIdentity.COMPONENT, identity.get()); break; }
        }
        return pickup;
    }
    /** Called immediately before a successful MerchantOffer.take, after the offer was validated. */
    public static void traded(Entity merchant, ItemStack cost) {
        ItemTrackingService.carry(merchant, cost, ItemTrackingService.Kind.ENTITY_HAS_ITEM, true);
    }
    public static boolean refusesTrade(LivingEntity merchant, Player player) { return merchant.getAttachedOrCreate(REFUSE_TRADE).contains(player.getUUID()); }
    public static boolean refuseInteraction(LivingEntity merchant, Player player) {
        if (merchant.level().isClientSide() || !refusesTrade(merchant, player)) return false;
        if (merchant instanceof Villager villager) {
            villager.setUnhappyCounter(40);
            float pitch = (villager.getRandom().nextFloat() - villager.getRandom().nextFloat()) * .2F + (villager.isBaby() ? 1.5F : 1);
            villager.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1, pitch);
        }
        return true;
    }
    /** Source extra pickup pass deliberately does not require Mob.canPickUpLoot(). */
    public static List<ItemEntity> markedPickupCandidates(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()
                || !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)) return List.of();
        return level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(1, 0, 1), item -> !item.isRemoved()
                && TrackedItemIdentity.get(item.getItem()).isPresent() && mob.wantsToPickUp(level, item.getItem()));
    }
    public static void playerJoin(ServerPlayer player) { entityChanged(player); ItemTrackingService.sync(player, true); }
    public static void playerTick(ServerPlayer player) { ItemTrackingService.tick(player); LifeformsEncountered.tick(player); }
    public static void livingTick(LivingEntity entity) { StandEffectStore.tickPresentation(entity); }
    public static void clearRuntime() { ItemTrackingService.clearRuntime(); LifeformsEncountered.clearRuntime(); ItemTrackingPayload.clearClient(); StandEffectStore.clearRuntime(); }
    static void onExtract(ServerPlayer thief, ItemTrackingService.Resolved resolved, ItemStack stack) {
        Entity holder = resolved.entity();
        if (resolved.location().kind() == ItemTrackingService.Kind.STUCK_ARROW && holder instanceof LivingEntity living)
            living.setArrowCount(Math.max(0, living.getArrowCount() - 1));
        if (resolved.location().kind() == ItemTrackingService.Kind.STUCK_KNIFE && holder instanceof LivingEntity living)
            living.setAttached(StuckKnives.COUNT, Math.max(0, StuckKnives.count(living) - 1));
        if (holder instanceof Villager villager) {
            villager.getGossips().add(thief.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
            resolved.level().broadcastEntityEvent(villager, (byte) 13);
            var refused = new LinkedHashSet<>(villager.getAttachedOrCreate(REFUSE_TRADE)); refused.add(thief.getUUID());
            if (refused.size() <= 1024) villager.setAttached(REFUSE_TRADE, List.copyOf(refused));
        } else if (holder instanceof Piglin piglin && stack.is(Items.GOLD_INGOT)) PiglinAccess.scammed(resolved.level(), piglin, thief);
        // Vanilla Container.setItem(empty) already stops jukebox playback, clears HAS_RECORD and updates item frames.
    }
    public static void initialize() {
        ItemTrackingService.initialize(); ItemTrackingPayload.initialize(); StandEffectStore.initialize(); LifeformsEncountered.initialize();
    }
    private TrackingHooks() { }
}
