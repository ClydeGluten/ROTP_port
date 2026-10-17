package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;

/** StandActionLearningProgress's per-Stand entries; absent means locked, zero means learned. */
public final class StandActionTraining {
    private static final int LIMIT = 4096;
    private static final Codec<String> ID = Codec.STRING.validate(id -> Identifier.tryParse(id) != null && id.length() <= 256
            ? DataResult.success(id) : DataResult.error(() -> "Invalid action/Stand identifier"));
    private static final Codec<Float> POINTS = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "Invalid learning points"));
    public record Entry(String stand, String action, float points) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(ID.fieldOf("stand").forGetter(Entry::stand),
                ID.fieldOf("action").forGetter(Entry::action), POINTS.fieldOf("points").forGetter(Entry::points)).apply(i, Entry::new));
    }
    public record State(List<Entry> entries) {
        public static final State EMPTY = new State(List.of());
        public State { entries = List.copyOf(entries); }
    }
    public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(s -> 1),
            Entry.CODEC.listOf(0, LIMIT).fieldOf("entries").forGetter(State::entries)).apply(i, (version, entries) -> new State(entries)));
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of((b, s) -> {
        b.writeVarInt(s.entries.size());
        for (var e : s.entries) { b.writeUtf(e.stand, 256); b.writeUtf(e.action, 256); b.writeFloat(e.points); }
    }, b -> {
        int n = b.readVarInt(); if (n < 0 || n > LIMIT) throw new IllegalArgumentException("Too many learned actions");
        var entries = new ArrayList<Entry>(n);
        for (int j = 0; j < n; j++) {
            String stand = b.readUtf(256), action = b.readUtf(256); float points = b.readFloat();
            if (Identifier.tryParse(stand) == null || Identifier.tryParse(action) == null || !Float.isFinite(points) || points < 0)
                throw new IllegalArgumentException("Invalid learned action");
            entries.add(new Entry(stand, action, points));
        }
        return new State(entries);
    });
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .persistent(CODEC).copyOnDeath().syncWith(STREAM, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("stand_action_learning"));

    public static float rawPoints(LivingEntity user, String stand, Identifier action) {
        return user.getAttachedOrCreate(DATA).entries.stream().filter(e -> e.stand.equals(stand) && e.action.equals(action.toString()))
                .findFirst().map(Entry::points).orElse(-1F);
    }
    public static float points(LivingEntity user, Identifier action, float maximum) {
        return Math.min(rawPoints(user, user.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), action), maximum);
    }
    public static boolean unlocked(LivingEntity user, Identifier action) {
        return rawPoints(user, user.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), action) >= 0;
    }
    public static boolean unlock(ServerPlayer user, Identifier action, boolean trained, float maximum) {
        if (unlocked(user, action)) return false;
        set(user, action, !trained || StandConfig.skipsTraining(user) ? maximum : 0); return true;
    }
    public static void set(LivingEntity user, Identifier action, float points) {
        if (user.level().isClientSide() || !Float.isFinite(points) || points < 0) return;
        var progress = user.getAttachedOrCreate(StandAttachments.PROGRESS); if (!progress.hasStand()) return;
        var entries = new ArrayList<>(user.getAttachedOrCreate(DATA).entries);
        entries.removeIf(e -> e.stand.equals(progress.standId()) && e.action.equals(action.toString()));
        if (entries.size() >= LIMIT) throw new IllegalStateException("Learned action limit exceeded");
        entries.add(new Entry(progress.standId(), action.toString(), points)); user.setAttached(DATA, new State(entries));
    }
    public static void add(ServerPlayer user, Identifier action, float amount, float maximum, float resolveMultiplier) {
        if (!Float.isFinite(amount) || !Float.isFinite(maximum) || maximum < 0 || !Float.isFinite(resolveMultiplier) || resolveMultiplier < 0) return;
        if (amount > 0 && StandAttachments.resolve(user).burstTicks() > 0) amount *= resolveMultiplier;
        float old = rawPoints(user, user.getAttachedOrCreate(StandAttachments.PROGRESS).standId(), action);
        // Source preserves training above a temporarily reduced maximum (notably species Time Stop).
        float next = Math.max(old + amount, 0); if (old <= maximum) next = Math.min(next, maximum);
        set(user, action, next);
    }
    public static void clear(LivingEntity user) { if (!user.level().isClientSide()) user.setAttached(DATA, State.EMPTY); }
    public static void initialize() { }
    private StandActionTraining() { }
}
