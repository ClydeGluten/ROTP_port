package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.core.PillarmanProgress.Mode;
import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server-owned LIGHT holds and continuous attacks. Input carries intent only; targets come from server geometry. */
public final class PillarLightActions {
    public enum Phase { NONE, FLASH, DECOY, SLASH, DASH_CHARGE, DASH, BARRAGE }
    public record State(Phase phase, int ticks) { public static final State EMPTY = new State(Phase.NONE, 0); }
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of((b, s) -> {
        b.writeByte(s.phase.ordinal()); b.writeVarInt(s.ticks);
    }, b -> {
        int phase = b.readUnsignedByte(), ticks = b.readVarInt();
        if (phase >= Phase.values().length || ticks < 0) throw new IllegalArgumentException("Invalid LIGHT state");
        return new State(Phase.values()[phase], ticks);
    });
    public static final AttachmentType<State> STATE = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .syncWith(STREAM, AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("pillarman_light_action"));
    public static final Holder<MobEffect> FULL_INVISIBILITY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("full_invisibility"), new FullInvisibility());
    public static final SimpleParticleType LIGHT_SPARK = particle("light_spark"), LIGHT_MODE_FLASH = particle("light_mode_flash"), RAINBOW_AURA = particle("hamon_aura_rainbow");
    public static final Identifier FLASH = RipplesFabric.id("pillarman_light_flash"), DECOY = RipplesFabric.id("pillarman_light_flash_decoy");
    private static final Identifier MOVEMENT = RipplesFabric.id("pillarman_light");
    private static final Map<ServerPlayer, Runtime> RUNTIME = new WeakHashMap<>();
    private static final class Runtime {
        Phase phase; int ticks, heartbeat; ResourceKey<Level> dimension;
        final Set<UUID> struck = new HashSet<>();
    }
    private static final class FullInvisibility extends MobEffect {
        FullInvisibility() { super(MobEffectCategory.BENEFICIAL, MobEffects.INVISIBILITY.value().getColor()); }
    }
    private static SimpleParticleType particle(String id) { return Registry.register(BuiltInRegistries.PARTICLE_TYPE, RipplesFabric.id(id), FabricParticleTypes.simple(false)); }
    /** Original tracking-emitter packet: positive count means spark emitters; zero means the intensity-two flash. */
    public record Emitter(int entityId, int count, boolean decoy) implements CustomPacketPayload {
        public static final Type<Emitter> TYPE = new Type<>(RipplesFabric.id("pillar_light_emitter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Emitter> CODEC = StreamCodec.of((b, value) -> {
            b.writeVarInt(value.entityId); b.writeVarInt(value.count); b.writeBoolean(value.decoy);
        }, b -> {
            int entity = b.readVarInt(), count = b.readVarInt(); boolean decoy = b.readBoolean();
            if (entity < 0 || count < 0 || count > 60) throw new IllegalArgumentException("Invalid LIGHT emitter");
            return new Emitter(entity, count, decoy);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static boolean initialized;
    public static void initialize() { if (!initialized) { initialized = true; PayloadTypeRegistry.clientboundPlay().register(Emitter.TYPE, Emitter.CODEC); } }
    public static State state(LivingEntity entity) { return entity.getAttachedOrCreate(STATE); }
    public static boolean active(ServerPlayer player) { return RUNTIME.containsKey(player); }
    /** Cancel the entire living-entity submission, including equipment, outline and carried layers. */
    public static boolean hidden(LivingEntity entity) { return entity.hasEffect(FULL_INVISIBILITY); }
    public static boolean uncurable(Holder<MobEffect> effect) { return effect.equals(FULL_INVISIBILITY); }
    private static boolean eligible(ServerPlayer player) {
        return PillarmanBaseActions.canContinue(player, 1, false, false) && PillarmanService.state(player).mode() == Mode.LIGHT;
    }
    public static boolean flash(ServerPlayer player) { return startHeld(player, Phase.FLASH); }
    public static boolean decoy(ServerPlayer player) { return startHeld(player, Phase.DECOY); }
    public static boolean dashCharge(ServerPlayer player) { return startHeld(player, Phase.DASH_CHARGE); }
    public static boolean barrage(ServerPlayer player) { return startHeld(player, Phase.BARRAGE); }
    public static boolean slash(ServerPlayer player) { return start(player, Phase.SLASH); }
    public static boolean startHeld(ServerPlayer player, Phase phase) {
        return switch (phase) { case FLASH, DECOY, DASH_CHARGE, BARRAGE -> start(player, phase); default -> false; };
    }
    private static boolean start(ServerPlayer player, Phase phase) {
        if (!eligible(player) || active(player) || !PillarmanBaseActions.canStart(player, 1, false, false)
                || (phase == Phase.SLASH || phase == Phase.BARRAGE) && !player.getMainHandItem().isEmpty()
                || phase == Phase.DASH_CHARGE && !player.onGround()
                || !player.isCreative() && PillarmanService.state(player).energy() < cost(phase)
                || phase == Phase.FLASH && AbilityCooldowns.remaining(player, FLASH) > 0
                || phase == Phase.DECOY && AbilityCooldowns.remaining(player, DECOY) > 0) return false;
        PillarmanBaseActions.stopHeld(player);
        if (phase == Phase.SLASH && !PillarmanService.spend(player, 10)) return false;
        var runtime = new Runtime(); runtime.phase = phase; runtime.dimension = player.level().dimension(); RUNTIME.put(player, runtime);
        StandMovement.actionFactor(player, MOVEMENT, phase == Phase.SLASH ? .5F : phase == Phase.BARRAGE ? .2F : 0);
        PillarmanService.setBlades(player, phase != Phase.SLASH);
        sync(player, runtime); return true;
    }
    private static float cost(Phase phase) { return switch (phase) { case FLASH -> 25; case DECOY -> 40; case SLASH -> 10; case DASH_CHARGE -> 50; case BARRAGE -> 1; default -> 0; }; }
    public static void keepHeld(ServerPlayer player) { var runtime = RUNTIME.get(player); if (runtime != null) runtime.heartbeat = 0; }
    public static void release(ServerPlayer player, boolean fire) {
        var runtime = RUNTIME.get(player); if (runtime == null || runtime.phase == Phase.SLASH || runtime.phase == Phase.DASH) return;
        Phase phase = runtime.phase;
        boolean ready = eligible(player) && runtime.dimension.equals(player.level().dimension()) && runtime.heartbeat <= 20
                && runtime.ticks >= (phase == Phase.FLASH ? 40 : phase == Phase.DECOY ? 50 : 10)
                && (phase != Phase.DASH_CHARGE || player.onGround());
        if (phase == Phase.BARRAGE || !fire || !ready) { clear(player); return; }
        if (!PillarmanService.spend(player, cost(phase))) { clear(player); return; }
        if (phase == Phase.DASH_CHARGE) {
            // Read movement attribute after restoring the held zero-speed modifier.
            StandMovement.releaseAction(player, MOVEMENT);
            Vec3 leap = Vec3.directionFromRotation(Math.clamp(player.getXRot(), -45F, -18F), player.getYRot())
                    .scale(1 + player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 20);
            player.setDeltaMovement(leap.x, leap.y * .05F, leap.z); player.syncVelocity = true; player.needsSync = true;
            runtime.phase = Phase.DASH; runtime.ticks = 0;
            StandMovement.actionFactor(player, MOVEMENT, 0); sync(player, runtime);
        } else {
            clear(player);
            if (phase == Phase.FLASH) PillarLightCombat.flash(player);
            else player.addEffect(new MobEffectInstance(FULL_INVISIBILITY, 100, 0, false, false, false));
            PillarmanService.sound(player, "aja_stone_beam", 2, 1);
            PillarLightCombat.flashParticles(player, phase == Phase.DECOY);
            AbilityCooldowns.start(player, phase == Phase.FLASH ? FLASH : DECOY, player.isCreative() ? 0 : phase == Phase.FLASH ? 80 : 120);
        }
    }
    public static void tick(ServerPlayer player) {
        var runtime = RUNTIME.get(player); if (runtime == null || TimeStopService.isFrozen(player)) return;
        boolean continuous = runtime.phase == Phase.SLASH || runtime.phase == Phase.DASH;
        if (!eligible(player) || !runtime.dimension.equals(player.level().dimension())
                || !continuous && ++runtime.heartbeat > 20
                || (runtime.phase == Phase.SLASH || runtime.phase == Phase.BARRAGE) && !player.getMainHandItem().isEmpty()) { clear(player); return; }
        if (runtime.ticks < Integer.MAX_VALUE) runtime.ticks++;
        switch (runtime.phase) {
            case FLASH -> { if (runtime.ticks >= 40) { release(player, true); return; } }
            case DECOY -> { if (runtime.ticks >= 50) { release(player, true); return; } }
            case SLASH -> {
                if (runtime.ticks == 2) PillarmanService.setBlades(player, true);
                if (runtime.ticks == 6) swing(player, 1.25F);
                if (runtime.ticks == 8) PillarLightCombat.slash(player);
                if (runtime.ticks >= 20) { clear(player); return; }
            }
            case DASH -> {
                if (runtime.ticks == 1) swing(player, 1);
                PillarLightCombat.dash(player, runtime.struck);
                if (runtime.ticks >= 15) { clear(player); return; }
            }
            case BARRAGE -> {
                if (!PillarmanService.spend(player, 1)) { clear(player); return; }
                PillarLightCombat.barrage(player);
            }
            default -> { }
        }
        sync(player, runtime);
    }
    private static void swing(ServerPlayer player, float pitch) {
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true); PillarmanService.sound(player, "hamon_syo_swing", 1, pitch);
    }
    private static void sync(ServerPlayer player, Runtime runtime) { player.setAttached(STATE, new State(runtime.phase, runtime.ticks)); }
    public static void clear(ServerPlayer player) {
        RUNTIME.remove(player); player.setAttached(STATE, State.EMPTY); PillarmanService.setBlades(player, false); StandMovement.releaseAction(player, MOVEMENT);
    }
    public static void clearAllRuntime() { for (var player : List.copyOf(RUNTIME.keySet())) clear(player); }
    private PillarLightActions() { }
}
