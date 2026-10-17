package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.core.PillarmanProgress.Mode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.*;

public final class PillarHeatActions {
    public enum Phase { NONE, ERRATIC, GIANT, DETONATION }
    public record State(Phase phase, int ticks) { public static final State EMPTY = new State(Phase.NONE, 0); }
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of((b, s) -> {
        b.writeByte(s.phase.ordinal()); b.writeVarInt(s.ticks);
    }, b -> { int phase = b.readUnsignedByte(), ticks = b.readVarInt();
        if (phase >= Phase.values().length || ticks < 0 || ticks > 1_000_000) throw new IllegalArgumentException("Invalid HEAT action state");
        return new State(Phase.values()[phase], ticks); });
    public static final AttachmentType<State> STATE = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .syncWith(STREAM, AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("pillarman_heat_action"));
    public static final EntityType<PillarVeinEntity> VEINS = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("pillarman_veins"),
            EntityType.Builder.<PillarVeinEntity>of(PillarVeinEntity::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(8).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("pillarman_veins"))));
    public static final Identifier GIANT = RipplesFabric.id("pillarman_giant_carthwheel_prison");
    private static final Identifier MOVEMENT = RipplesFabric.id("pillarman_heat");
    private static final Map<ServerPlayer, Runtime> RUNTIME = new WeakHashMap<>();
    private static final class Runtime { Phase phase; int ticks, heartbeat; ResourceKey<net.minecraft.world.level.Level> dimension; }
    public static void initialize() {
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((user, level, hand, target, hit) -> {
            if (user instanceof ServerPlayer player && eatExplosive(player, target, hand)) return net.minecraft.world.InteractionResult.SUCCESS;
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    public static State state(LivingEntity user) { return user.getAttachedOrCreate(STATE); }
    public static boolean active(ServerPlayer player) { return RUNTIME.containsKey(player); }
    private static boolean eligible(ServerPlayer player) {
        return PillarmanBaseActions.canAct(player) && PillarmanService.state(player).mode() == Mode.HEAT
                && !PillarmanService.state(player).stoneForm() && !VampireCombatService.isStunned(player);
    }
    private static boolean start(ServerPlayer player, Phase phase, float cost) {
        if (!eligible(player) || active(player) || !PillarmanBaseActions.canStart(player, 1, false, false)
                || !player.isCreative() && PillarmanService.state(player).energy() < cost
                || phase == Phase.GIANT && AbilityCooldowns.remaining(player, GIANT) > 0) return false;
        PillarmanBaseActions.stopHeld(player);
        var runtime = new Runtime(); runtime.phase = phase; runtime.dimension = player.level().dimension(); RUNTIME.put(player, runtime);
        if (phase == Phase.ERRATIC) PillarmanService.spend(player, cost);
        StandMovement.actionFactor(player, MOVEMENT, phase == Phase.ERRATIC ? .1F : 0);
        player.setAttached(STATE, new State(phase, 0)); return true;
    }
    public static boolean erratic(ServerPlayer player) { return start(player, Phase.ERRATIC, 20); }
    public static boolean giant(ServerPlayer player) { return start(player, Phase.GIANT, 125); }
    public static boolean detonation(ServerPlayer player) { return start(player, Phase.DETONATION, 150); }
    public static void keepHeld(ServerPlayer player) { var runtime = RUNTIME.get(player); if (runtime != null) runtime.heartbeat = 0; }
    public static void release(ServerPlayer player, boolean fire) {
        var runtime = RUNTIME.get(player); if (runtime == null || runtime.phase == Phase.ERRATIC) return;
        boolean ready = runtime.ticks >= (runtime.phase == Phase.GIANT ? 30 : 60) && eligible(player);
        clear(player);
        if (!fire || !ready || !PillarmanService.spend(player, runtime.phase == Phase.GIANT ? 125 : 150)) return;
        if (runtime.phase == Phase.GIANT) {
            for (int i = 0; i < 8; i++) {
                float[] rotation = rotation(i / 8D * Math.PI * 2, 2);
                for (int yaw : new int[]{0, 180, 90, -90}) addVein(player, rotation[0], rotation[1] + yaw, 0, -.5, 0);
            }
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0, false, false));
            AbilityCooldowns.start(player, GIANT, player.isCreative() ? 0 : 100);
        } else {
            new PillarHeatExplosion(player).explode();
            if (!player.isCreative()) {
                player.hurtServer(player.level(), player.damageSources().explosion(player, player), 40);
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            }
        }
    }
    public static void tick(ServerPlayer player) {
        var runtime = RUNTIME.get(player); if (runtime == null || TimeStopService.isFrozen(player)) return;
        if (!eligible(player) || !runtime.dimension.equals(player.level().dimension())
                || runtime.phase != Phase.ERRATIC && ++runtime.heartbeat > 20) { clear(player); return; }
        runtime.ticks = Math.min(1_000_000, runtime.ticks + 1);
        if (runtime.phase == Phase.ERRATIC) {
            if (runtime.ticks == 10) for (int i = 0; i < 5; i++) {
                float[] rotation = rotation(i / 5D * Math.PI * 2, 10);
                addVein(player, rotation[0], rotation[1], -.4, -.45, 1);
                addVein(player, rotation[0], rotation[1], .425, -.575, 1);
            }
            if (runtime.ticks >= 40) { clear(player); return; }
        }
        player.setAttached(STATE, new State(runtime.phase, runtime.ticks));
    }
    private static void addVein(ServerPlayer owner, float pitch, float yaw, double x, double y, double z) {
        owner.level().addFreshEntity(new PillarVeinEntity(owner, pitch, yaw, x, y, z));
    }
    /** MathUtil.xRotYRotOffsets, including its signed pitch/yaw convention. */
    private static float[] rotation(double angle, double z) {
        double x = -Math.cos(angle), y = Math.sin(angle), total = x * x + y * y + z * z;
        double angleXZ = Math.acos(Math.sqrt((x * x + z * z) / total));
        double angleYZ = Math.acos(Math.sqrt((y * y + z * z) / total));
        if (angle > Math.PI) angleXZ = -angleXZ;
        if (angle > Math.PI / 2 && angle < Math.PI * 3 / 2) angleYZ = -angleYZ;
        return new float[]{(float)Math.toDegrees(angleYZ), (float)Math.toDegrees(angleXZ)};
    }
    public static void clear(ServerPlayer player) { RUNTIME.remove(player); player.setAttached(STATE, State.EMPTY); StandMovement.releaseAction(player, MOVEMENT); }
    public static void clearAllRuntime() { RUNTIME.clear(); }
    public static boolean eatExplosive(ServerPlayer player, Entity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !PillarmanService.hasPillarman(player) || PillarmanService.state(player).mode() != Mode.HEAT) return false;
        int fuse;
        if (target instanceof PrimedTnt tnt) { fuse = tnt.getFuse(); tnt.discard(); }
        else if (target instanceof MinecartTNT cart && cart.isPrimed()) {
            fuse = cart.getFuse();
            var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
            cart.saveWithoutId(saved);
            var replacement = EntityTypes.MINECART.create(player.level(), EntitySpawnReason.CONVERSION);
            if (replacement == null) return false;
            replacement.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
            var passengers = java.util.List.copyOf(cart.getPassengers());
            cart.ejectPassengers(); cart.discard(); player.level().addFreshEntity(replacement);
            for (var passenger : passengers) passenger.startRiding(replacement, true, true);
        } else return false;
        PillarmanService.addEatenTntFuse(player, fuse);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1, 1 + (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * .4F);
        return true;
    }
    private PillarHeatActions() { }
}
