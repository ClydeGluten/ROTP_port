package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.ZombieProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class ZombieAttachments {
    private record Stored(boolean zombie, float blood, float maximum, boolean disguised) { }
    public static final Codec<ZombieProgress> CODEC = RecordCodecBuilder.<Stored>create(i -> i.group(
            Codec.BOOL.optionalFieldOf("zombie", false).forGetter(Stored::zombie),
            Codec.FLOAT.optionalFieldOf("blood", 0F).forGetter(Stored::blood),
            Codec.FLOAT.optionalFieldOf("maximum", 1000F).forGetter(Stored::maximum),
            Codec.BOOL.optionalFieldOf("DisguiseEnabled", false).forGetter(Stored::disguised)
    ).apply(i, Stored::new)).comapFlatMap(s -> {
        try { return DataResult.success(new ZombieProgress(s.zombie, s.blood, s.maximum, s.disguised)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, p -> new Stored(p.zombie(), p.blood(), p.maxBlood(), p.disguised()));
    public static final StreamCodec<RegistryFriendlyByteBuf, ZombieProgress> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeBoolean(p.zombie()); b.writeFloat(p.blood()); b.writeFloat(p.maxBlood()); b.writeBoolean(p.disguised()); },
            b -> new ZombieProgress(b.readBoolean(), b.readFloat(), b.readFloat(), b.readBoolean()));
    // No copyOnDeath: explicit alive-clone hook preserves state only on non-death replacement.
    public static final AttachmentType<ZombieProgress> PROGRESS = AttachmentRegistry.<ZombieProgress>builder()
            .initializer(() -> ZombieProgress.EMPTY).persistent(CODEC)
            .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("zombie_progress"));
    public enum Held { NONE, DEVOUR, DISGUISE }
    public record Hud(Held held, int heldTicks, int clawTicks, int leapCooldown) {
        public static final Hud EMPTY = new Hud(Held.NONE, 0, 0, 0);
    }
    public record Visual(boolean skin, boolean glowing, int clawTicks, int fadeTicks, boolean leftHanded, boolean devouring) {
        public static final Visual EMPTY = new Visual(false, false, 0, 0, false, false);
    }
    public static final AttachmentType<Hud> HUD = AttachmentRegistry.<Hud>builder().initializer(() -> Hud.EMPTY)
            .syncWith(StreamCodec.<RegistryFriendlyByteBuf,Hud>of((b,s) -> { b.writeEnum(s.held); b.writeVarInt(s.heldTicks); b.writeVarInt(s.clawTicks); b.writeVarInt(s.leapCooldown); },
                    b -> new Hud(b.readEnum(Held.class), b.readVarInt(), b.readVarInt(), b.readVarInt())), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(RipplesFabric.id("zombie_hud"));
    public static final AttachmentType<Visual> VISUAL = AttachmentRegistry.<Visual>builder().initializer(() -> Visual.EMPTY)
            .syncWith(StreamCodec.<RegistryFriendlyByteBuf,Visual>of((b,s) -> { b.writeBoolean(s.skin); b.writeBoolean(s.glowing); b.writeVarInt(s.clawTicks); b.writeVarInt(s.fadeTicks); b.writeBoolean(s.leftHanded); b.writeBoolean(s.devouring); },
                    b -> new Visual(b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean())), AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("zombie_visual"));
    public static void initialize() { }
    private ZombieAttachments() { }
}
