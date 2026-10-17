package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandEffects;
import com.github.standobyte.jojo.fabric.stand.tracking.ItemTrackingService;
import com.github.standobyte.jojo.fabric.stand.tracking.StandEffectStore;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Source single-mark lifetime and hand movement; holders are always resolved by the shared real-slot tracker. */
public final class GoldExperienceItemActions {
    public static final Identifier ITEM_MARK = RipplesFabric.id("ge_item_mark");
    public static final Identifier MARK_ACTION = RipplesFabric.id("gold_experience_mark_item");
    public static Optional<UUID> itemId(StandEffects.Entry entry) { return entry.data().read("ItemTracker", UUIDUtil.CODEC); }
    public static void initialize() {
        StandEffects.register(ITEM_MARK, new StandEffects.Handler() {
            @Override public void start(LivingEntity owner, StandEffects.Entry effect, Entity target) {
                if (owner instanceof ServerPlayer player) itemId(effect).ifPresent(id -> ItemTrackingService.restoreRegistration(player, id));
            }
            @Override public StandEffects.Entry tick(LivingEntity owner, StandEffects.Entry effect, Entity target) {
                return owner instanceof ServerPlayer player && itemId(effect).filter(id -> ItemTrackingService.isRegistered(player, id)).isPresent() ? effect : null;
            }
            @Override public void stop(LivingEntity owner, StandEffects.Entry effect, Entity target) {
                if (owner instanceof ServerPlayer player) itemId(effect).ifPresent(id -> ItemTrackingService.unmark(player, id));
            }
            @Override public boolean needsTarget() { return false; }
        });
    }
    public static boolean canMark(ServerPlayer player) {
        ItemStack held = player.getOffhandItem(); return !held.isEmpty() && GoldExperienceTransformation.canGiveLifeTo(held);
    }
    public static boolean mark(ServerPlayer player) {
        if (!canMark(player)) return false;
        ItemStack offhand = player.getOffhandItem(); ItemStack marked; boolean give = false;
        if (offhand.getCount() == 1) {
            marked = offhand;
            if (!player.getMainHandItem().isEmpty()) { player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); give = true; }
            else { player.setItemInHand(InteractionHand.OFF_HAND, player.getMainHandItem()); player.setItemInHand(InteractionHand.MAIN_HAND, offhand); }
        } else {
            marked = offhand.split(1);
            if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, marked);
            else give = true;
        }
        player.stopUsingItem(); clearMarks(player);
        var identity = ItemTrackingService.mark(player, marked);
        if (identity.isPresent()) {
            var data = new CompoundTag(); data.store("ItemTracker", UUIDUtil.CODEC, identity.get().id());
            StandEffects.add(player, ITEM_MARK, null, data);
            com.github.standobyte.jojo.fabric.StandPerception.playSound(player, "gold_experience_life_item", .5F, 1);
        }
        if (give && !player.getInventory().add(marked)) player.drop(marked, false);
        ItemTrackingService.entityChanged(player); return identity.isPresent();
    }
    public static void clearMarks(ServerPlayer player) { StandEffectStore.removeType(player, ITEM_MARK); }
    public static List<ItemTrackingService.Resolved> targets(ServerPlayer player) {
        var targets = StandEffectStore.ofType(player, ITEM_MARK).map(GoldExperienceItemActions::itemId).flatMap(Optional::stream)
                .map(id -> ItemTrackingService.resolve(player, id)).flatMap(Optional::stream)
                .filter(resolved -> resolved.entity() != player && resolved.level() == player.level()
                        && resolved.markerPosition().distanceToSqr(player.position()) < 128 * 128).toList();
        return targets.size() > 1 ? List.of(targets.getLast()) : targets;
    }
    public static Optional<ItemTrackingService.Resolved> lookedAt(ServerPlayer player) {
        Vec3 look = player.getLookAngle(), eye = player.getEyePosition();
        return targets(player).stream().filter(target -> look.dot(target.markerPosition().subtract(eye).normalize()) > .92388)
                .max(Comparator.comparingDouble(target -> look.dot(target.markerPosition().subtract(eye).normalize())));
    }
    /** A submitted UUID selects only the owner's currently eligible mark; it never chooses a remote holder. */
    public static Optional<ItemTrackingService.Resolved> selected(ServerPlayer player, UUID id) {
        return targets(player).stream().filter(resolved -> resolved.identity().id().equals(id)).findFirst();
    }
    private GoldExperienceItemActions() { }
}
