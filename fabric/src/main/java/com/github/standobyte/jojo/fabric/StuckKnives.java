package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** LivingStuckObjects.StuckObjectsTracker: persistent count and a transient, progressively slower removal timer. */
public final class StuckKnives {
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> COUNT_STREAM = StreamCodec.of(
            (buffer, count) -> buffer.writeVarInt(count), RegistryFriendlyByteBuf::readVarInt);
    public static final AttachmentType<Integer> COUNT = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0).persistent(Codec.INT)
            .syncWith(COUNT_STREAM, AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("stuck_knives"));
    private static final AttachmentType<Integer> REMOVE_TIME = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0).buildAndRegister(RipplesFabric.id("stuck_knives_remove_time"));

    public static void initialize() { }

    public static int count(LivingEntity entity) { return Math.max(0, entity.getAttachedOrElse(COUNT, 0)); }

    public static void add(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel) entity.setAttached(COUNT, Math.max(0, count(entity) + 1));
    }

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        int count = count(entity);
        if (count <= 0) return;
        int remaining = entity.getAttachedOrElse(REMOVE_TIME, 0);
        if (remaining <= 0) remaining = 20 * (30 - count);
        remaining--;
        entity.setAttached(REMOVE_TIME, remaining);
        if (remaining <= 0) entity.setAttached(COUNT, count - 1);
    }

    private StuckKnives() { }
}
