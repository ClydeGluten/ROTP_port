package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.stand.tracking.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual holders, entity save/load and world collisions exercise the root transfer hooks. */
public final class StandTrackingGameTests {
    private static ServerPlayer player(GameTestHelper test, String name) {
        var player = VampireGameTests.player(test, name, 1.5);
        player.snapTo(test.absoluteVec(new Vec3(2.5, 1, 1.5)), 0, 0); return player;
    }
    private static void close(ServerPlayer player) { player.level().getServer().getPlayerList().remove(player); }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void markedItemFollowsRealPickupContainerReloadAndDimensionTransfer(GameTestHelper test) {
        var owner = player(test, "tracked-holder"); var stranger = player(test, "tracked-stranger");
        try {
            var original = new ItemStack(Items.DIAMOND); original.set(DataComponents.CUSTOM_NAME, Component.literal("Saved marked diamond"));
            owner.setItemInHand(InteractionHand.OFF_HAND, original);
            var identity = ItemTrackingService.mark(owner, original).orElseThrow();
            ItemStack renderCopy = original.copy();
            test.assertTrue(ItemTrackingService.resolve(owner, identity.id()).orElseThrow().stack() == original,
                    "An ordinary copied stack does not steal the actual inventory holder");
            test.assertTrue(ItemTrackingService.resolve(stranger, identity.id()).isEmpty(), "Another player cannot resolve or extract this mark");
            test.assertTrue(ItemTrackingService.mark(owner, new ItemStack(Items.DIAMOND, 2)).isEmpty(), "Source marking requires one item");
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            var drop = new ItemEntity(test.getLevel(), owner.getX(), owner.getY(), owner.getZ(), original);
            drop.setNoPickUpDelay(); test.getLevel().addFreshEntity(drop);
            test.assertTrue(ItemTrackingService.resolve(owner, identity.id()).orElseThrow().entity() == drop, "Dropped item claims its real entity UUID");
            drop.playerTouch(owner);
            test.assertTrue(drop.isRemoved(), "Real vanilla pickup consumes the dropped entity");
            var pickedUp = ItemTrackingService.resolve(owner, identity.id()).orElseThrow();
            test.assertTrue(pickedUp.entity() == owner, "Inventory.add hook follows the copied pickup stack");
            ItemStack pickedStack = pickedUp.stack();
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
                if (identity.matches(owner.getInventory().getItem(slot))) owner.getInventory().setItem(slot, ItemStack.EMPTY);
            BlockPos chestPos = new BlockPos(4, 1, 3); test.setBlock(chestPos, Blocks.CHEST);
            var chest = (ChestBlockEntity) test.getLevel().getBlockEntity(test.absolutePos(chestPos)); chest.setItem(0, pickedStack);
            test.assertTrue(ItemTrackingService.resolve(owner, identity.id()).orElseThrow().location().block().orElseThrow().equals(test.absolutePos(chestPos)),
                    "Actual block container insertion changes the holder");
            var savedOwner = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess()); owner.saveWithoutId(savedOwner);
            var savedChest = chest.saveWithFullMetadata(test.getLevel().registryAccess());
            owner.setAttached(ItemTrackingService.DATA, ItemTrackingService.State.EMPTY); chest.clearContent(); ItemTrackingService.clearRuntime();
            owner.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), savedOwner.buildResult()));
            chest.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), savedChest));
            test.assertTrue(ItemTrackingService.resolve(owner, identity.id()).isPresent(), "Real player and container reload retains identity and actual holder");
            var restored = chest.removeItemNoUpdate(0);
            var dimensionDrop = new ItemEntity(test.getLevel(), owner.getX(), owner.getY(), owner.getZ(), restored); test.getLevel().addFreshEntity(dimensionDrop);
            var nether = test.getLevel().getServer().getLevel(Level.NETHER); test.assertTrue(nether != null, "Runtime includes the actual Nether dimension");
            var transferred = dimensionDrop.teleport(new TeleportTransition(nether, new Vec3(0, 100, 0), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            test.assertTrue(transferred != null && ItemTrackingService.resolve(owner, identity.id()).orElseThrow().level() == nether,
                    "Real dimension transfer follows holder UUID and dimension");
            test.assertTrue(ItemTrackingService.extract(owner, identity.id()).isEmpty(), "Cross-dimension remote extraction is rejected");
            var returned = transferred.teleport(new TeleportTransition(test.getLevel(), owner.position(), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            var extracted = ItemTrackingService.extract(owner, identity.id()).orElseThrow();
            test.assertTrue(returned.isRemoved() && extracted.stack().getCount() == 1, "Extraction removes the real represented entity exactly once");
            test.assertValueEqual(extracted.stack().get(DataComponents.CUSTOM_NAME).getString(), "Saved marked diamond", "Full item components survive transfers and save/load");
            test.assertTrue(ItemTrackingService.extract(owner, identity.id()).isEmpty(), "Repeated extraction cannot mint another item");
            test.assertTrue(TrackedItemIdentity.get(extracted.stack()).isEmpty(), "Consumed mark is cleared from the returned item");
            // A previously copied identity is rejected when it is later placed in a real holder.
            owner.setItemInHand(InteractionHand.OFF_HAND, renderCopy); TrackingHooks.entityChanged(owner);
            test.assertTrue(TrackedItemIdentity.get(renderCopy).isEmpty(), "An old component copy cannot resurrect a consumed registration");
            test.succeed();
        } finally { close(owner); close(stranger); }
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void firedMarkedArrowBecomesEmbeddedAndCanOnlyBeTakenOnce(GameTestHelper test) {
        var owner = player(test, "tracked-arrow");
        try {
            var ammo = new ItemStack(Items.ARROW); owner.setItemInHand(InteractionHand.OFF_HAND, ammo);
            var identity = ItemTrackingService.mark(owner, ammo).orElseThrow();
            var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 1, 5)); target.setNoGravity(true);
            var arrow = new Arrow(test.getLevel(), owner, ammo, null);
            arrow.setPos(test.absoluteVec(new Vec3(2.5, 1.7, 2.5))); arrow.setDeltaMovement(0, 0, 2); arrow.setNoGravity(true);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); test.getLevel().addFreshEntity(arrow);
            for (int i = 0; i < 3 && !arrow.isRemoved(); i++) { arrow.commonTick(); arrow.tick(); }
            var resolved = ItemTrackingService.resolve(owner, identity.id()).orElseThrow();
            test.assertTrue(resolved.entity() == target && resolved.location().kind() == ItemTrackingService.Kind.STUCK_ARROW,
                    "Actual arrow collision transfers the single marked object to its living host");
            int count = target.getArrowCount();
            test.assertTrue(ItemTrackingService.extract(owner, identity.id()).orElseThrow().stack().is(Items.ARROW), "Embedded extraction retains the original ammunition");
            test.assertValueEqual(target.getArrowCount(), Math.max(0, count - 1), "Source extraction decrements embedded-arrow rendering count");
            test.assertTrue(ItemTrackingService.extract(owner, identity.id()).isEmpty(), "Embedded object cannot be extracted twice");
            target.discard(); test.succeed();
        } finally { close(owner); }
    }
    private static final Identifier EFFECT = RipplesFabric.id("test_linked_effect");
    private static final Map<UUID, Integer> STARTS = new HashMap<>(), STOPS = new HashMap<>();
    private static boolean registered;
    private static void effect() {
        if (registered) return; registered = true;
        StandEffects.register(EFFECT, StandEffectStore.expiring(3, new StandEffects.Handler() {
            public void start(LivingEntity owner, StandEffects.Entry entry, Entity target) { STARTS.merge(entry.id(), 1, Integer::sum); }
            public StandEffects.Entry tick(LivingEntity owner, StandEffects.Entry entry, Entity target) { return entry; }
            public void stop(LivingEntity owner, StandEffects.Entry entry, Entity target) { STOPS.merge(entry.id(), 1, Integer::sum); }
            public boolean needsTarget() { return true; }
        }));
    }
    @GameTest(environment="jojo:vampire_night", setupTicks=5)
    public void npcLinkedEffectsAndEncounteredLifeformsSurviveSaveWithBoundedLifecycle(GameTestHelper test) {
        effect(); var owner = player(test, "encounter-save");
        try {
            var npc = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1, 4)); npc.setNoGravity(true);
            var target = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(4.5, 1, 6)); target.setNoGravity(true);
            var entry = StandEffectStore.getOrCreate(npc, EFFECT, target, CompoundTag::new).orElseThrow();
            StandEffects.tick(npc);
            test.assertValueEqual(StandEffectStore.getOrCreate(npc, EFFECT, target, CompoundTag::new).orElseThrow().ticks(), 1,
                    "Reusing a source effect never refreshes its age");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess()); npc.saveWithoutId(saved);
            var loaded = EntityTypes.ZOMBIE.create(test.getLevel(), EntitySpawnReason.COMMAND);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved.buildResult()));
            var duplicate = StandEffects.entries(loaded).getFirst(); loaded.setAttached(StandEffects.DATA, new StandEffects.State(List.of(duplicate, duplicate)));
            test.assertValueEqual(StandEffects.entries(loaded).size(), 1, "Duplicate persisted effect UUIDs are canonicalized");
            target.discard();
            StandEffects.tick(loaded);
            test.assertTrue(StandEffectStore.byId(loaded, entry.id()).orElseThrow().target().isPresent(), "Unloaded target preserves its identity for future resolution");
            StandEffects.tick(loaded); StandEffects.tick(loaded);
            test.assertTrue(StandEffectStore.byId(loaded, entry.id()).isEmpty(), "Finite linked effect expires on its persisted source age");
            test.assertValueEqual(STOPS.getOrDefault(entry.id(), 0), 1, "Expired effect stop callback runs once, even after a duplicate load");
            test.assertTrue(STARTS.getOrDefault(entry.id(), 0) == 2, "Effect starts once per actual loaded owner instance");
            var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 1, 4));
            test.assertTrue(LifeformsEncountered.encounter(owner, cow), "Server records the encountered actual entity type");
            var cowKey = LifeformSubtypes.Key.base(Identifier.withDefaultNamespace("cow"));
            test.assertTrue(LifeformsEncountered.eligible(owner, cowKey), "Encountered cow passes the original lifeform predicate");
            test.assertFalse(LifeformSubtypes.isValid(npc), "Original undead lifeforms are excluded");
            test.assertFalse(LifeformsEncountered.eligible(owner, LifeformSubtypes.Key.parse("minecraft:cow#invented").orElseThrow()), "Unsupported variants cannot be selected");
            var playerSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess()); owner.saveWithoutId(playerSave);
            owner.setAttached(LifeformsEncountered.DATA, LifeformsEncountered.State.EMPTY);
            owner.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), playerSave.buildResult()));
            test.assertTrue(LifeformsEncountered.contains(owner, cowKey), "Actual player save/load retains discovered lifeforms independently of Stand ownership");
            cow.discard(); npc.discard(); test.succeed();
        } finally { close(owner); }
    }
}
