package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class PillarmanAttachments {
    /** Transient clocks for the existing HUD and the later original animation/layer port. */
    public record Actions(int held, int heldTicks, int punchTicks, int regenerationCooldown, int evasionCooldown, int leapCooldown) {
        public static final Actions EMPTY = new Actions(0, 0, 0, 0, 0, 0);
    }
    private record Stored(boolean pillarman, int stage, String mode, boolean stone, float energy, float maximum) { }
    public static final Codec<PillarmanProgress> CODEC = RecordCodecBuilder.<Stored>create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("pillarman", false).forGetter(Stored::pillarman),
            Codec.intRange(1, 4).optionalFieldOf("PillarmanStage", 1).forGetter(Stored::stage),
            Codec.STRING.optionalFieldOf("PillarmanMode", "NONE").forGetter(Stored::mode),
            Codec.BOOL.optionalFieldOf("StoneForm", false).forGetter(Stored::stone),
            Codec.FLOAT.optionalFieldOf("energy", 0F).forGetter(Stored::energy),
            Codec.FLOAT.optionalFieldOf("maximum", 1000F).forGetter(Stored::maximum)
    ).apply(instance, Stored::new)).comapFlatMap(value -> {
        try { return DataResult.success(new PillarmanProgress(value.pillarman, value.stage, PillarmanProgress.Mode.valueOf(value.mode), value.stone, value.energy, value.maximum)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, value -> new Stored(value.pillarman(), value.stage(), value.mode().name(), value.stoneForm(), value.energy(), value.maximum()));
    public static final StreamCodec<RegistryFriendlyByteBuf, PillarmanProgress> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBoolean(value.pillarman()); buf.writeVarInt(value.stage()); buf.writeEnum(value.mode()); buf.writeBoolean(value.stoneForm()); buf.writeFloat(value.energy()); buf.writeFloat(value.maximum()); },
            buf -> new PillarmanProgress(buf.readBoolean(), buf.readVarInt(), buf.readEnum(PillarmanProgress.Mode.class), buf.readBoolean(), buf.readFloat(), buf.readFloat()));
    public static final AttachmentType<PillarmanProgress> PROGRESS = AttachmentRegistry.<PillarmanProgress>builder()
            .initializer(() -> PillarmanProgress.EMPTY).persistent(CODEC).copyOnDeath()
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("pillarman_progress"));
    public static final AttachmentType<Boolean> BLADES = AttachmentRegistry.<Boolean>builder().initializer(() -> false)
            .syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("pillarman_blades"));
    public static final AttachmentType<Integer> SENSES_TICKS = AttachmentRegistry.<Integer>builder().initializer(() -> 0)
            .syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("pillarman_senses_ticks"));
    public static final AttachmentType<Actions> ACTIONS = AttachmentRegistry.<Actions>builder().initializer(() -> Actions.EMPTY)
            .syncWith(StreamCodec.of((RegistryFriendlyByteBuf buf, Actions value) -> {
                buf.writeVarInt(value.held()); buf.writeVarInt(value.heldTicks()); buf.writeVarInt(value.punchTicks());
                buf.writeVarInt(value.regenerationCooldown()); buf.writeVarInt(value.evasionCooldown()); buf.writeVarInt(value.leapCooldown());
            }, buf -> new Actions(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())), AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("pillarman_actions"));
    public static void initialize() { }
    private PillarmanAttachments() { }
}
