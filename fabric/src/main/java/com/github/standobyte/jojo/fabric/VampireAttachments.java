package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.VampireProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class VampireAttachments {
    private record Stored(boolean vampire, boolean fullPower, float blood, float maxBlood) { }
    public static final Codec<VampireProgress> CODEC = RecordCodecBuilder.<Stored>create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("vampire", false).forGetter(Stored::vampire),
            Codec.BOOL.optionalFieldOf("full_power", false).forGetter(Stored::fullPower),
            Codec.FLOAT.optionalFieldOf("blood", 0F).forGetter(Stored::blood),
            Codec.FLOAT.optionalFieldOf("maximum", 1000F).forGetter(Stored::maxBlood)
    ).apply(instance, Stored::new)).comapFlatMap(value -> {
        try { return DataResult.success(new VampireProgress(value.vampire, value.fullPower, value.blood, value.maxBlood)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, value -> new Stored(value.vampire(), value.fullPower(), value.blood(), value.maxBlood()));
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireProgress> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeBoolean(value.vampire()); buffer.writeBoolean(value.fullPower()); buffer.writeFloat(value.blood()); buffer.writeFloat(value.maxBlood()); },
            buffer -> new VampireProgress(buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readFloat()));
    public static final AttachmentType<VampireProgress> PROGRESS = AttachmentRegistry.<VampireProgress>builder()
            .initializer(() -> VampireProgress.EMPTY).persistent(CODEC).copyOnDeath()
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("vampire_progress"));
    public static final AttachmentType<VampireHudState> HUD = AttachmentRegistry.<VampireHudState>builder()
            .initializer(() -> VampireHudState.EMPTY).syncWith(VampireHudState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("vampire_hud"));
    public static final AttachmentType<Integer> CURING = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0).persistent(Codec.intRange(0, Integer.MAX_VALUE)).copyOnDeath()
            .syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("vampire_curing"));
    public static final AttachmentType<Boolean> COFFIN_RESPAWN = AttachmentRegistry.<Boolean>builder()
            .initializer(() -> false).persistent(Codec.BOOL)
            .syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("vampire_coffin_respawn"));
    public static void initialize() { }
    private VampireAttachments() { }
}
