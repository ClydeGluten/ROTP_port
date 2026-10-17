package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Authoritative source tracker. Resolving a marker always rechecks the real item holder. */
public final class ItemTrackingService {
    public enum Kind { ENTITY_IS_ITEM, ENTITY_HAS_ITEM, STUCK_ARROW, STUCK_KNIFE, BLOCK_IS_ITEM, BLOCK_HAS_ITEM }
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
        try { return DataResult.success(Kind.valueOf(name)); }
        catch (IllegalArgumentException e) { return DataResult.error(() -> "Invalid tracked holder kind"); }
    }, Enum::name);
    public record Location(Identifier dimension, Optional<UUID> entity, Optional<BlockPos> block,
                           Optional<Identifier> blockType, Kind kind) {
        public static final Codec<Location> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(Location::dimension),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Location::entity),
                BlockPos.CODEC.optionalFieldOf("block").forGetter(Location::block),
                Identifier.CODEC.optionalFieldOf("block_type").forGetter(Location::blockType),
                KIND_CODEC.fieldOf("kind").forGetter(Location::kind)).apply(i, Location::new));
        public Location { block = block.map(BlockPos::immutable); }
    }
    public record Mark(UUID id, Optional<Location> location) {
        public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Mark::id),
                Location.CODEC.optionalFieldOf("location").forGetter(Mark::location)).apply(i, Mark::new));
    }
    public record State(List<Mark> marks) {
        public static final State EMPTY = new State(List.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(s -> 1),
                Mark.CODEC.listOf(0, 128).fieldOf("marks").forGetter(State::marks)).apply(i, (version, marks) -> new State(marks)));
        public State {
            var unique = new LinkedHashMap<UUID, Mark>();
            marks.forEach(mark -> unique.putIfAbsent(mark.id, mark)); marks = List.copyOf(unique.values());
        }
    }
    /** Projectiles/embedded arrows/traded-away items need a real serialized item despite lacking a vanilla slot. */
    public record Carried(ItemStack stack, Kind kind) {
        public static final Codec<Carried> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(Carried::stack),
                KIND_CODEC.fieldOf("kind").forGetter(Carried::kind)).apply(i, Carried::new));
    }
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .persistent(State.CODEC).copyOnDeath().buildAndRegister(RipplesFabric.id("tracked_items"));
    public static final AttachmentType<List<Carried>> CARRIED = AttachmentRegistry.<List<Carried>>builder().initializer(List::of)
            .persistent(Carried.CODEC.listOf(0, 128)).buildAndRegister(RipplesFabric.id("carried_tracked_items"));
    private record Adapter(Class<? extends Entity> type, Function<Entity, ItemStack> get, ConsumerPair set) { }
    @FunctionalInterface public interface ConsumerPair { void accept(Entity entity, ItemStack stack); }
    private static final List<Adapter> ADAPTERS = new ArrayList<>();
    private static final Map<Entity, Container> INVENTORIES = new WeakHashMap<>();
    private static final Map<UUID, java.lang.ref.WeakReference<Entity>> LIVE_ENTITIES = new HashMap<>();
    private static final Map<ServerPlayer, List<ItemTrackingPayload.View>> LAST_SENT = new WeakHashMap<>();

    private record Slot(Supplier<ItemStack> get, Consumer<ItemStack> set, Runnable changed) {
        ItemStack stack() { return get.get(); }
        void clearIdentity() {
            ItemStack current = stack(); current.remove(TrackedItemIdentity.COMPONENT);
            set.accept(current.copy()); changed.run();
        }
        ItemStack take() { ItemStack result = stack().copy(); set.accept(ItemStack.EMPTY); changed.run(); return result; }
    }
    public record Resolved(TrackedItemIdentity identity, Location location, ItemStack stack, Entity entity,
                           ServerLevel level, Vec3 markerPosition) { }
    public record Extracted(ItemStack stack, Location location, Entity formerHolder, Vec3 position) { }
    public static void registerEntityItem(Class<? extends Entity> type, Function<Entity, ItemStack> getter, ConsumerPair setter) {
        if (ADAPTERS.stream().anyMatch(a -> a.type == type)) throw new IllegalStateException("Duplicate tracked entity item adapter");
        ADAPTERS.add(new Adapter(type, getter, setter));
    }
    public static void bindInventory(Entity holder, Container inventory) { INVENTORIES.put(holder, inventory); }
    public static List<Mark> marks(ServerPlayer owner) { return owner.getAttachedOrCreate(DATA).marks; }
    public static boolean isRegistered(ServerPlayer owner, UUID id) { return marks(owner).stream().anyMatch(m -> m.id.equals(id)); }
    public static Optional<TrackedItemIdentity> mark(ServerPlayer owner, ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1) return Optional.empty();
        var old = TrackedItemIdentity.get(stack);
        if (old.isPresent()) return old.filter(i -> i.owner().equals(owner.getUUID()) && isRegistered(owner, i.id()));
        if (marks(owner).size() >= 128) return Optional.empty();
        var identity = new TrackedItemIdentity(UUID.randomUUID(), owner.getUUID());
        stack.set(TrackedItemIdentity.COMPONENT, identity);
        var next = new ArrayList<>(marks(owner)); next.add(new Mark(identity.id(), Optional.empty()));
        owner.setAttached(DATA, new State(next));
        entityChanged(owner);
        return Optional.of(identity);
    }
    /** Used when a persisted GE mark effect resumes; it never accepts an owner supplied by a client. */
    public static void restoreRegistration(ServerPlayer owner, UUID id) {
        if (isRegistered(owner, id) || marks(owner).size() >= 128) return;
        var next = new ArrayList<>(marks(owner)); next.add(new Mark(id, Optional.empty())); owner.setAttached(DATA, new State(next));
    }
    public static void unmark(ServerPlayer owner, UUID id) {
        Mark mark = marks(owner).stream().filter(m -> m.id.equals(id)).findFirst().orElse(null);
        if (mark == null) return;
        locate(owner, mark).ifPresent(slot -> slot.clearIdentity());
        owner.setAttached(DATA, new State(marks(owner).stream().filter(m -> !m.id.equals(id)).toList()));
        sync(owner, false);
    }
    private static ServerPlayer owner(ServerLevel level, TrackedItemIdentity identity) {
        return level.getServer().getPlayerList().getPlayer(identity.owner());
    }
    private static Location atEntity(Entity entity, Kind kind) {
        return new Location(entity.level().dimension().identifier(), Optional.of(entity.getUUID()), Optional.empty(), Optional.empty(), kind);
    }
    private static Location atBlock(ServerLevel level, BlockPos pos) {
        return new Location(level.dimension().identifier(), Optional.empty(), Optional.of(pos.immutable()),
                Optional.of(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())), Kind.BLOCK_HAS_ITEM);
    }
    private static void observe(ServerLevel level, Slot slot, Location location) {
        ItemStack stack = slot.stack(); var identity = TrackedItemIdentity.get(stack).orElse(null);
        if (identity == null) return;
        ServerPlayer owner = owner(level, identity);
        // Offline owners are deliberately unresolved. Loading a chunk must not erase their saved identities.
        if (owner == null) return;
        Mark mark = marks(owner).stream().filter(m -> m.id.equals(identity.id())).findFirst().orElse(null);
        if (mark == null) { slot.clearIdentity(); return; }
        if (mark.location.filter(location::equals).isPresent()) return;
        // Only a real slot observation can transfer identity. ItemStack.copy() has no registration side effect.
        locate(owner, mark).filter(previous -> previous.stack() != stack).ifPresent(Slot::clearIdentity);
        update(owner, new Mark(identity.id(), Optional.of(location)));
    }
    private static void update(ServerPlayer owner, Mark changed) {
        var next = marks(owner).stream().map(m -> m.id.equals(changed.id) ? changed : m).toList();
        owner.setAttached(DATA, new State(next));
    }
    private static List<Slot> containerSlots(Container container) {
        var result = new ArrayList<Slot>();
        for (int index = 0; index < container.getContainerSize(); index++) {
            int slot = index; result.add(new Slot(() -> container.getItem(slot), stack -> container.setItem(slot, stack), container::setChanged));
        }
        return result;
    }
    private static List<Slot> entitySlots(Entity entity, Kind kind) {
        var result = new ArrayList<Slot>();
        if (kind == Kind.ENTITY_IS_ITEM) {
            if (entity instanceof ItemEntity item) result.add(new Slot(item::getItem, item::setItem, () -> { }));
            boolean adapted = false;
            for (Adapter adapter : ADAPTERS) if (adapter.type.isInstance(entity))
                { result.add(new Slot(() -> adapter.get.apply(entity), stack -> adapter.set.accept(entity, stack), () -> { })); adapted = true; }
            if (!adapted && entity instanceof AbstractArrow arrow)
                result.add(new Slot(arrow::getPickupItemStackOrigin, stack -> arrow.getPickupItemStackOrigin().setCount(stack.getCount()), () -> { }));
        } else if (kind == Kind.ENTITY_HAS_ITEM) {
            if (entity instanceof ItemFrame frame) result.add(new Slot(frame::getItem, frame::setItem, () -> { }));
            if (entity instanceof Player player) result.addAll(containerSlots(player.getInventory()));
            if (entity instanceof Container container) result.addAll(containerSlots(container));
            if (entity instanceof InventoryCarrier carrier) result.addAll(containerSlots(carrier.getInventory()));
            Container bound = INVENTORIES.get(entity); if (bound != null) result.addAll(containerSlots(bound));
            if (entity instanceof LivingEntity living) for (EquipmentSlot slot : EquipmentSlot.values())
                result.add(new Slot(() -> living.getItemBySlot(slot), stack -> living.setItemSlot(slot, stack), () -> { }));
        }
        for (Carried entry : List.copyOf(entity.getAttachedOrCreate(CARRIED))) if (entry.kind == kind) {
            result.add(new Slot(() -> entity.getAttachedOrCreate(CARRIED).contains(entry) ? entry.stack : ItemStack.EMPTY,
                    stack -> {
                        var next = new ArrayList<>(entity.getAttachedOrCreate(CARRIED)); next.remove(entry);
                        if (!stack.isEmpty()) next.add(new Carried(stack, kind)); entity.setAttached(CARRIED, List.copyOf(next));
                    }, () -> entity.setAttached(CARRIED, List.copyOf(entity.getAttachedOrCreate(CARRIED)))));
        }
        return result;
    }
    public static void entityChanged(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isRemoved()) LIVE_ENTITIES.put(entity.getUUID(), new java.lang.ref.WeakReference<>(entity));
        var seen = new HashMap<TrackedItemIdentity, ItemStack>();
        for (Kind kind : List.of(Kind.ENTITY_IS_ITEM, Kind.ENTITY_HAS_ITEM, Kind.STUCK_ARROW, Kind.STUCK_KNIFE)) {
            Location location = atEntity(entity, kind);
            for (Slot slot : entitySlots(entity, kind)) {
                var identity = TrackedItemIdentity.get(slot.stack()).orElse(null);
                if (identity != null) {
                    ItemStack previous = seen.putIfAbsent(identity, slot.stack());
                    if (previous != null && previous != slot.stack()) { slot.clearIdentity(); continue; }
                }
                observe(level, slot, location);
            }
        }
    }
    public static void inventoryChanged(LivingEntity holder, Container inventory) {
        bindInventory(holder, inventory); entityChanged(holder);
    }
    public static void containerChanged(ServerLevel level, BlockPos pos, Container container) {
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != container) return;
        Location location = atBlock(level, pos); var seen = new HashSet<TrackedItemIdentity>();
        for (Slot slot : containerSlots(container)) {
            var identity = TrackedItemIdentity.get(slot.stack()).orElse(null);
            if (identity != null && !seen.add(identity)) slot.clearIdentity(); else observe(level, slot, location);
        }
    }
    private static ServerLevel dimension(ServerPlayer owner, Location location) {
        return owner.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, location.dimension));
    }
    private static Entity findEntity(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity != null) return entity;
        var ref = LIVE_ENTITIES.get(id); Entity cached = ref == null ? null : ref.get();
        return cached != null && !cached.isRemoved() && cached.level() == level ? cached : null;
    }
    private static List<Slot> slots(ServerPlayer owner, Location location) {
        ServerLevel level = dimension(owner, location); if (level == null) return List.of();
        if (location.entity.isPresent()) {
            Entity entity = findEntity(level, location.entity.get());
            return entity == null || entity.isRemoved() ? List.of() : entitySlots(entity, location.kind);
        }
        if (location.block.isPresent() && level.hasChunkAt(location.block.get())) {
            BlockPos pos = location.block.get();
            if (location.blockType.filter(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())::equals).isEmpty()) return List.of();
            if (level.getBlockEntity(pos) instanceof Container container) return containerSlots(container);
        }
        return List.of();
    }
    private static Optional<Slot> locate(ServerPlayer owner, Mark mark) {
        var identity = new TrackedItemIdentity(mark.id, owner.getUUID());
        return mark.location.stream().flatMap(location -> slots(owner, location).stream()).filter(slot -> identity.matches(slot.stack())).findFirst();
    }
    public static Optional<Resolved> resolve(ServerPlayer owner, UUID id) {
        Mark mark = marks(owner).stream().filter(m -> m.id.equals(id)).findFirst().orElse(null);
        if (mark == null || mark.location.isEmpty()) return Optional.empty();
        Location location = mark.location.get(); var slot = locate(owner, mark); if (slot.isEmpty()) return Optional.empty();
        ServerLevel level = dimension(owner, location);
        Entity entity = location.entity.map(uuid -> findEntity(level, uuid)).orElse(null);
        Vec3 marker = entity != null ? entity.position().add(0, entity.getBbHeight() + .25, 0)
                : Vec3.atBottomCenterOf(location.block.orElseThrow()).add(0, 1, 0);
        return Optional.of(new Resolved(new TrackedItemIdentity(id, owner.getUUID()), location, slot.get().stack(), entity, level, marker));
    }
    /** Exactly-once removal of the actual slot, including source holder side effects. */
    public static Optional<Extracted> extract(ServerPlayer owner, UUID id) {
        var resolved = resolve(owner, id).orElse(null); if (resolved == null || resolved.level != owner.level()) return Optional.empty();
        Mark mark = marks(owner).stream().filter(m -> m.id.equals(id)).findFirst().orElseThrow();
        var slot = locate(owner, mark).orElse(null); if (slot == null) return Optional.empty();
        owner.setAttached(DATA, new State(marks(owner).stream().filter(m -> !m.id.equals(id)).toList()));
        ItemStack result = slot.take(); result.remove(TrackedItemIdentity.COMPONENT);
        TrackingHooks.onExtract(owner, resolved, result);
        if (resolved.location.kind == Kind.ENTITY_IS_ITEM && resolved.entity != null) resolved.entity.discard();
        sync(owner, false);
        return Optional.of(new Extracted(result, resolved.location, resolved.entity, resolved.markerPosition));
    }
    public static void carry(Entity entity, ItemStack stack, Kind kind, boolean detachSourceIdentity) {
        if (!(entity.level() instanceof ServerLevel) || TrackedItemIdentity.get(stack).isEmpty()) return;
        ItemStack copy = stack.copy(); var identity = TrackedItemIdentity.get(copy).orElseThrow();
        if (kind == Kind.ENTITY_IS_ITEM && entity instanceof AbstractArrow arrow && arrow.getPickupItemStackOrigin().is(copy.getItem()))
            arrow.getPickupItemStackOrigin().applyComponents(copy.getComponentsPatch());
        var next = new ArrayList<>(entity.getAttachedOrCreate(CARRIED));
        next.removeIf(entry -> identity.matches(entry.stack) || TrackedItemIdentity.get(entry.stack).isEmpty());
        if (next.size() >= 128) return;
        next.add(new Carried(copy, kind)); entity.setAttached(CARRIED, List.copyOf(next));
        if (detachSourceIdentity) stack.remove(TrackedItemIdentity.COMPONENT);
        entityChanged(entity);
    }
    public static void embedded(Entity projectile, LivingEntity target, boolean knife) {
        if (!(target.level() instanceof ServerLevel)) return;
        var tracked = entitySlots(projectile, Kind.ENTITY_IS_ITEM).stream().map(Slot::stack)
                .filter(s -> TrackedItemIdentity.get(s).isPresent()).map(ItemStack::copy).toList();
        for (ItemStack stack : tracked) carry(target, stack, knife ? Kind.STUCK_KNIFE : Kind.STUCK_ARROW, false);
        projectile.setAttached(CARRIED, List.of());
    }
    public static ItemStack preferredProjectile(Player player, ItemStack fallback, java.util.function.Predicate<ItemStack> supported) {
        if (TrackedItemIdentity.get(fallback).isPresent()) return fallback;
        for (Slot slot : containerSlots(player.getInventory())) if (supported.test(slot.stack()) && TrackedItemIdentity.get(slot.stack()).isPresent()) return slot.stack();
        return fallback;
    }
    public static void tick(ServerPlayer owner) { entityChanged(owner); sync(owner, false); }
    public static void sync(ServerPlayer owner, boolean force) {
        var views = marks(owner).stream().map(mark -> resolve(owner, mark.id)).flatMap(Optional::stream)
                .filter(r -> r.level == owner.level()).map(ItemTrackingPayload.View::of).toList();
        if (force || !views.equals(LAST_SENT.get(owner))) {
            LAST_SENT.put(owner, views); ItemTrackingPayload.send(owner, views);
        }
    }
    public static void clearRuntime() { INVENTORIES.clear(); LIVE_ENTITIES.clear(); LAST_SENT.clear(); }
    public static void initialize() { TrackedItemIdentity.initialize(); }
    private ItemTrackingService() { }
}
