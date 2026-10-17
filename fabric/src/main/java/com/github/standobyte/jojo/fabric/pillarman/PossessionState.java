package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import java.util.UUID;

/** Persistent original Possessed/previous-mode/context state; no attachment means no possession. */
public record PossessionState(UUID host, Identifier dimension, GameType previousMode, boolean asAlive, Identifier context) {
    public static final Identifier CONTEXT = RipplesFabric.id("pillarman_hide_in_entity");
    public static final Identifier ANGELO = RipplesFabric.id("angelo_rock");
    public static final Codec<PossessionState> CODEC = RecordCodecBuilder.<PossessionState>create(instance -> instance.group(
            Codec.intRange(1, 1).fieldOf("version").forGetter(value -> 1),
            UUIDUtil.CODEC.fieldOf("Possessed").forGetter(PossessionState::host),
            Identifier.CODEC.fieldOf("dimension").forGetter(PossessionState::dimension),
            GameType.CODEC.fieldOf("PossessPrevMode").forGetter(PossessionState::previousMode),
            Codec.BOOL.fieldOf("PossessAsAlive").forGetter(PossessionState::asAlive),
            Identifier.CODEC.fieldOf("Ctx").forGetter(PossessionState::context)
    ).apply(instance, (version, host, dimension, previous, alive, context) -> new PossessionState(host, dimension, previous, alive, context)));
    // Retain the recovery marker across respawn until root's lifecycle callback restores the previous mode.
    public static final AttachmentType<PossessionState> TYPE = AttachmentRegistry.<PossessionState>builder()
            .persistent(CODEC).copyOnDeath().buildAndRegister(RipplesFabric.id("pillar_possession"));
    public PossessionState(UUID host, Identifier dimension, GameType previousMode) { this(host, dimension, previousMode, true, CONTEXT); }
    public PossessionState {
        java.util.Objects.requireNonNull(host); java.util.Objects.requireNonNull(dimension); java.util.Objects.requireNonNull(previousMode);java.util.Objects.requireNonNull(context);
        if (asAlive != context.equals(CONTEXT) || !asAlive && !context.equals(ANGELO)) throw new IllegalArgumentException("Invalid possession context");
    }
    public PossessionState previousMode(GameType mode) { return new PossessionState(host, dimension, mode, asAlive, context); }
    public static void initialize() { }
}
