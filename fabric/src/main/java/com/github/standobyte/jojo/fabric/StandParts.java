package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandDefinitions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Original StandInstance parts. New/legacy complete instances default to all three parts. */
public final class StandParts {
    public enum Part { MAIN_BODY, ARMS, LEGS }
    public record State(String stand, int present) {
        public static final State COMPLETE = new State("", 7);
        public State { if (stand.length() > 256 || present < 0 || present > 7) throw new IllegalArgumentException("Invalid Stand parts"); }
    }
    public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(s -> 1),
            Codec.STRING.optionalFieldOf("stand", "").forGetter(State::stand),
            Codec.intRange(0, 7).optionalFieldOf("present", 7).forGetter(State::present)).apply(i, (version, stand, present) -> new State(stand, present)));
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of(
            (b, s) -> { b.writeUtf(s.stand, 256); b.writeByte(s.present); }, b -> new State(b.readUtf(256), b.readUnsignedByte()));
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.COMPLETE)
            .persistent(CODEC).copyOnDeath().syncWith(STREAM, AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("stand_parts"));
    public static int mask(LivingEntity user) {
        var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS); if (!progress.hasStand()) return 0;
        var parts = user.getAttachedOrCreate(DATA);
        return parts.stand.equals(progress.standId()) ? parts.present : 7;
    }
    public static boolean has(LivingEntity user, Part part) { return (mask(user) & 1 << part.ordinal()) != 0; }
    public static boolean remove(LivingEntity user, Part part) {
        if (user.level().isClientSide() || !has(user, part)) return false;
        set(user, mask(user) & ~(1 << part.ordinal())); return true;
    }
    public static boolean add(LivingEntity user, Part part) {
        if (user.level().isClientSide() || has(user, part) || !user.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()) return false;
        set(user, mask(user) | 1 << part.ordinal()); return true;
    }
    public static void set(LivingEntity user, int present) {
        if (!user.level().isClientSide()) user.setAttached(DATA, new State(user.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), present));
    }
    public static void reset(LivingEntity user) { if (!user.level().isClientSide()) user.setAttached(DATA, State.COMPLETE); }
    /** EntityStandType.tickUser: these are ordinary lingering effects, not a new movement multiplier. */
    public static void tick(LivingEntity user) {
        var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS);
        if (user.level().isClientSide() || !progress.hasStand() || StandDefinitions.definition(progress.standId()).kind() != StandDefinitions.Kind.ENTITY) return;
        if (!has(user, Part.ARMS)) {
            user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 319, 1));
            user.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 319, 1));
        }
        if (!has(user, Part.LEGS)) user.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 319, 1));
    }
    public static void initialize() { }
    private StandParts() { }
}
