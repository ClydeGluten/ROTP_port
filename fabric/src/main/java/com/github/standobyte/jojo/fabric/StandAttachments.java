package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.core.ResolveProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class StandAttachments {
    private static final Codec<Float> STAMINA_CODEC = Codec.floatRange(0, Float.MAX_VALUE)
            .validate(value -> Float.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Stamina must be finite"));
    private static final Codec<Float> DEVELOPMENT_CODEC = Codec.floatRange(0, 1)
            .validate(value -> Float.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Development must be finite"));
    public static final Codec<StandProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("stand").forGetter(StandProgress::standId),
            STAMINA_CODEC.fieldOf("stamina").forGetter(StandProgress::stamina),
            DEVELOPMENT_CODEC.optionalFieldOf("development", 0F).forGetter(StandProgress::development),
            Codec.BOOL.optionalFieldOf("had_stand", false).forGetter(StandProgress::hadStand)
    ).apply(instance, StandProgress::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StandProgress> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUtf(value.standId(), 256);
                buffer.writeFloat(value.stamina());
                buffer.writeFloat(value.development());
                buffer.writeBoolean(value.hadStand());
            },
            buffer -> new StandProgress(buffer.readUtf(256), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean()));

    public static final AttachmentType<StandProgress> PROGRESS = AttachmentRegistry.<StandProgress>builder()
            .initializer(() -> StandProgress.EMPTY)
            .persistent(CODEC)
            .copyOnDeath()
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_progress"));

    public static final Codec<ArrowProgress> ARROW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 1_000_000).optionalFieldOf("acquired", 0).forGetter(ArrowProgress::acquired),
            Codec.intRange(0, 100_000_620).optionalFieldOf("remaining", 0).forGetter(ArrowProgress::remainingTicks),
            Codec.intRange(0, 5_000_030).optionalFieldOf("paid", 0).forGetter(ArrowProgress::paidLevels),
            Codec.intRange(0, 5_000_030).optionalFieldOf("required", 0).forGetter(ArrowProgress::requiredLevels),
            Codec.BOOL.optionalFieldOf("healing", false).forGetter(ArrowProgress::healing)
    ).apply(instance, ArrowProgress::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArrowProgress> ARROW_STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.acquired()); buffer.writeVarInt(value.remainingTicks());
                buffer.writeVarInt(value.paidLevels()); buffer.writeVarInt(value.requiredLevels()); buffer.writeBoolean(value.healing());
            }, buffer -> new ArrowProgress(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
    public static final AttachmentType<ArrowProgress> ARROW = AttachmentRegistry.<ArrowProgress>builder()
            .initializer(() -> ArrowProgress.EMPTY).persistent(ARROW_CODEC).copyOnDeath()
            .syncWith(ARROW_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_arrow_progress"));

    public static final Codec<ResolveProgress> RESOLVE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 4).optionalFieldOf("level", 0).forGetter(ResolveProgress::level),
            Codec.floatRange(0, 50000).optionalFieldOf("resolve", 0F).forGetter(ResolveProgress::resolve),
            Codec.intRange(0, 400).optionalFieldOf("no_decay", 0).forGetter(ResolveProgress::noDecayTicks),
            Codec.floatRange(1, 5).optionalFieldOf("attack_boost", 1F).forGetter(ResolveProgress::attackBoost),
            Codec.FLOAT.optionalFieldOf("health_record", -1F).forGetter(ResolveProgress::healthRecord),
            Codec.FLOAT.listOf(0, 10).optionalFieldOf("records", java.util.List.of()).forGetter(ResolveProgress::records),
            Codec.BOOL.optionalFieldOf("save_record", true).forGetter(ResolveProgress::saveNextRecord),
            Codec.intRange(0, 2400).optionalFieldOf("burst_ticks", 0).forGetter(ResolveProgress::burstTicks),
            Codec.intRange(0, 4).optionalFieldOf("burst_level", 0).forGetter(ResolveProgress::burstLevel),
            Codec.floatRange(0, 95).optionalFieldOf("training", 0F).forGetter(ResolveProgress::trainingPoints)
    ).apply(instance, ResolveProgress::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResolveProgress> RESOLVE_STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.level()); buffer.writeFloat(value.resolve()); buffer.writeVarInt(value.noDecayTicks());
                buffer.writeFloat(value.attackBoost()); buffer.writeFloat(value.healthRecord());
                buffer.writeVarInt(value.records().size()); for (float record : value.records()) buffer.writeFloat(record);
                buffer.writeBoolean(value.saveNextRecord()); buffer.writeVarInt(value.burstTicks());
                buffer.writeVarInt(value.burstLevel()); buffer.writeFloat(value.trainingPoints());
            }, buffer -> {
                int level = buffer.readVarInt(); float value = buffer.readFloat(); int delay = buffer.readVarInt();
                float boost = buffer.readFloat(), health = buffer.readFloat(); int count = buffer.readVarInt();
                if (count < 0 || count > 10) throw new IllegalArgumentException("Invalid Resolve record count");
                var records = new java.util.ArrayList<Float>(); for (int i = 0; i < count; i++) records.add(buffer.readFloat());
                return new ResolveProgress(level, value, delay, boost, health, records, buffer.readBoolean(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
            });
    public static final AttachmentType<ResolveProgress> RESOLVE = AttachmentRegistry.<ResolveProgress>builder()
            .initializer(() -> ResolveProgress.EMPTY).persistent(RESOLVE_CODEC).copyOnDeath()
            .syncWith(RESOLVE_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_resolve"));

    public static final AttachmentType<ResolveProgress> THE_WORLD_RESOLVE = AttachmentRegistry.<ResolveProgress>builder()
            .initializer(() -> ResolveProgress.EMPTY).persistent(RESOLVE_CODEC).copyOnDeath()
            .syncWith(RESOLVE_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("the_world_resolve"));

    public static AttachmentType<ResolveProgress> resolveType(net.minecraft.world.entity.player.Player player) {
        return player.getAttachedOrCreate(PROGRESS).hasTheWorld() ? THE_WORLD_RESOLVE : RESOLVE;
    }
    public static ResolveProgress resolve(net.minecraft.world.entity.player.Player player) {
        return player.getAttachedOrCreate(resolveType(player));
    }
    public static final AttachmentType<StandHudState> HUD = AttachmentRegistry.<StandHudState>builder()
            .initializer(() -> StandHudState.EMPTY)
            .syncWith(StandHudState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_hud"));

    public static void initialize() { }
    private StandAttachments() { }
}
