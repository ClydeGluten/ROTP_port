package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The original TrackerItemStack Id/Player pair. Holder information is never trusted from a stack. */
public record TrackedItemIdentity(UUID id, UUID owner) {
    public static final Codec<TrackedItemIdentity> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(TrackedItemIdentity::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(TrackedItemIdentity::owner)).apply(i, TrackedItemIdentity::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackedItemIdentity> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUUID(v.id); b.writeUUID(v.owner); },
            b -> new TrackedItemIdentity(b.readUUID(), b.readUUID()));
    public static final DataComponentType<TrackedItemIdentity> COMPONENT = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, RipplesFabric.id("tracked_item"),
            DataComponentType.<TrackedItemIdentity>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());
    public TrackedItemIdentity { Objects.requireNonNull(id); Objects.requireNonNull(owner); }
    public static Optional<TrackedItemIdentity> get(ItemStack stack) {
        return stack.isEmpty() || stack.getCount() != 1 ? Optional.empty() : Optional.ofNullable(stack.get(COMPONENT));
    }
    public boolean matches(ItemStack stack) { return get(stack).filter(this::equals).isPresent(); }
    public static void initialize() { }
}
