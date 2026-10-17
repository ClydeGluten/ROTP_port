package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import java.util.Map;
import java.util.WeakHashMap;

/** Four original WIND actions. Mode is sufficient at any Pillar stage; runtime is server-owned. */
public final class PillarWindActions {
    public enum Held { NONE, DIVINE, CLOAK, RIFT }
    public enum Action { SMALL_SANDSTORM, START_DIVINE, START_CLOAK, START_RIFT, KEEP_HELD, STOP_HELD }
    public record State(Held held, int ticks, int smallCooldown, int cloakCooldown) {
        public static final State EMPTY = new State(Held.NONE, 0, 0, 0);
    }
    public static final EntityType<PillarSandstormEntity> SANDSTORM_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            RipplesFabric.id("pillarman_divine_sandstorm"), EntityType.Builder.<PillarSandstormEntity>of(PillarSandstormEntity::new, MobCategory.MISC)
                    .sized(4, 2).fireImmune().updateInterval(20).build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("pillarman_divine_sandstorm"))));
    public static final SimpleParticleType SANDSTORM = particle("divine_sandstorm"), RIFT = particle("atmospheric_rift"), GREEN_AURA = particle("hamon_aura_green");
    private static SimpleParticleType particle(String id) { return Registry.register(BuiltInRegistries.PARTICLE_TYPE, RipplesFabric.id(id), FabricParticleTypes.simple(false)); }
    private static final class SunResistance extends MobEffect { SunResistance() { super(MobEffectCategory.BENEFICIAL, 0xFFD54A); } }
    public static final Holder<MobEffect> SUN_RESISTANCE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, RipplesFabric.id("sun_resistance"), new SunResistance());
    public static boolean uncurable(Holder<MobEffect> effect) { return effect.equals(SUN_RESISTANCE); }
    public static final AttachmentType<State> STATE = AttachmentRegistry.<State>builder().initializer(() -> State.EMPTY)
            .syncWith(StreamCodec.of((RegistryFriendlyByteBuf buf, State value) -> {
                buf.writeByte(value.held.ordinal()); buf.writeVarInt(value.ticks); buf.writeVarInt(value.smallCooldown); buf.writeVarInt(value.cloakCooldown);
            }, buf -> {
                int held = buf.readUnsignedByte(), ticks = buf.readVarInt(), small = buf.readVarInt(), cloak = buf.readVarInt();
                if (held >= Held.values().length || ticks < 0 || small < 0 || small > 10 || cloak < 0 || cloak > 100)
                    throw new io.netty.handler.codec.DecoderException("Invalid Pillar WIND state");
                return new State(Held.values()[held], ticks, small, cloak);
            }), AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("pillar_wind_actions"));
    public record Intent(Action action) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(RipplesFabric.id("pillar_wind_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of((buf, value) -> buf.writeByte(value.action.ordinal()), buf -> {
            int action = buf.readUnsignedByte(); if (action >= Action.values().length) throw new io.netty.handler.codec.DecoderException("Invalid WIND action");
            return new Intent(Action.values()[action]);
        });
        @Override public Type<Intent> type() { return TYPE; }
    }
    private static final net.minecraft.resources.Identifier SPEED = RipplesFabric.id("pillar_wind_movement");
    private static final class Runtime {
        Held held = Held.NONE; int ticks, heartbeat, smallCooldown, cloakCooldown;
        net.minecraft.resources.Identifier dimension;
    }
    private static final Map<ServerPlayer, Runtime> RUNTIME = new WeakHashMap<>();
    private static boolean initialized;
    public static void initialize() {
        if (initialized) return; initialized = true;
        PayloadTypeRegistry.serverboundPlay().register(Intent.TYPE, Intent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Intent.TYPE, (payload, context) -> receive(context.player(), payload.action()));
    }
    public static State state(LivingEntity player) { return player.getAttachedOrCreate(STATE); }
    public static boolean isHolding(ServerPlayer player) { var runtime = RUNTIME.get(player); return runtime != null && runtime.held != Held.NONE; }
    private static Runtime runtime(ServerPlayer player) { return RUNTIME.computeIfAbsent(player, ignored -> new Runtime()); }
    private static boolean eligible(ServerPlayer player) {
        return PillarmanBaseActions.canContinue(player, 1, false, false) && PillarmanService.state(player).mode() == PillarmanProgress.Mode.WIND;
    }
    public static boolean receive(ServerPlayer player, Action action) {
        return switch (action) {
            case SMALL_SANDSTORM -> smallSandstorm(player);
            case START_DIVINE -> startHeld(player, Held.DIVINE);
            case START_CLOAK -> startHeld(player, Held.CLOAK);
            case START_RIFT -> startHeld(player, Held.RIFT);
            case KEEP_HELD -> { keepHeld(player); yield true; }
            case STOP_HELD -> { stopHeld(player); yield true; }
        };
    }
    public static boolean smallSandstorm(ServerPlayer player) {
        if (!eligible(player) || !PillarmanBaseActions.canStart(player, 1, false, false) || isHolding(player) || runtime(player).smallCooldown > 0 || !PillarmanService.spend(player, 20)) return false;
        emit(player, false, 1.5F, 6, 40, 1.5F, 1, .2F);
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        var runtime = runtime(player); runtime.smallCooldown = player.isCreative() ? 0 : 10;
        runtime.dimension = player.level().dimension().identifier(); sync(player, runtime); return true;
    }
    public static boolean startHeld(ServerPlayer player, Held held) {
        if (held == Held.NONE || !eligible(player) || !PillarmanBaseActions.canStart(player, 1, false, false) || isHolding(player)
                || held == Held.CLOAK && (runtime(player).cloakCooldown > 0 || !player.isCreative() && PillarmanService.state(player).energy() < 2)) return false;
        PillarmanBaseActions.stopHeld(player);
        var runtime = runtime(player); runtime.held = held; runtime.ticks = 0; runtime.heartbeat = 0;
        runtime.dimension = player.level().dimension().identifier(); sync(player, runtime); return true;
    }
    public static void keepHeld(ServerPlayer player) { var runtime = RUNTIME.get(player); if (runtime != null) runtime.heartbeat = 0; }
    public static void stopHeld(ServerPlayer player) {
        var runtime = RUNTIME.get(player); if (runtime == null) return;
        if (runtime.held == Held.CLOAK) runtime.cloakCooldown = player.isCreative() ? 0 : 100;
        runtime.held = Held.NONE; runtime.ticks = 0; runtime.heartbeat = 0; sync(player, runtime);
        // Source effects expire naturally after their remaining five ticks; never remove someone else's invisibility.
    }
    public static void tick(ServerPlayer player) {
        var runtime = RUNTIME.get(player); if (runtime == null) return;
        if (!player.isAlive() || player.isRemoved() || !PillarmanService.hasPillarman(player)
                || runtime.dimension != null && !runtime.dimension.equals(player.level().dimension().identifier())) { clear(player); return; }
        if (TimeStopService.isFrozen(player)) return;
        if (runtime.smallCooldown > 0) runtime.smallCooldown--;
        if (runtime.cloakCooldown > 0) runtime.cloakCooldown--;
        if (runtime.held == Held.NONE) { sync(player, runtime); return; }
        if (!eligible(player) || ++runtime.heartbeat > 15) { stopHeld(player); return; }
        // Source PowerBaseImpl increments then tests maximum, so CLOAK ends before applying tick200.
        runtime.ticks++;
        if (runtime.held == Held.CLOAK && runtime.ticks >= 200) { stopHeld(player); return; }
        float cost = runtime.held == Held.CLOAK ? 2 : runtime.ticks >= 40 ? 3 : 0;
        float energy = PillarmanService.state(player).energy();
        if (!player.isCreative() && energy < cost || runtime.held != Held.CLOAK && energy == 0) { stopHeld(player); return; }
        if (runtime.held == Held.CLOAK) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false));
            player.addEffect(new MobEffectInstance(SUN_RESISTANCE, 5, 0, false, false));
        } else if (runtime.ticks >= 40 && runtime.ticks % 2 == 0 && energy > 0) {
            if (runtime.held == Held.DIVINE) emit(player, false, 1.5F, 2, 10, .9F, 2, .2F);
            else {
                emit(player, true, .5F, 2, 60, 1.75F, 1, .1F);
                // Original generic.bypassArmor still obeys ordinary hurt immunity; never use StandDamage here.
                if (!player.isCreative()) player.hurtServer(player.level(), player.damageSources().generic(), 1);
            }
        }
        PillarmanService.spend(player, cost); sync(player, runtime);
    }
    private static void emit(ServerPlayer player, boolean atmospheric, float radius, float damage, int duration, float speed, float spread, float volume) {
        var entity = new PillarSandstormEntity(player).configure(atmospheric, radius, damage, duration);
        entity.shootFrom(player, speed, spread); player.level().addFreshEntity(entity);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get("magicians_red_fire_ability"), SoundSource.AMBIENT, volume, 1);
    }
    private static void sync(ServerPlayer player, Runtime runtime) {
        var state = new State(runtime.held, runtime.ticks, runtime.smallCooldown, runtime.cloakCooldown);
        if (!state.equals(state(player))) player.setAttached(STATE, state);
        double factor = runtime.held == Held.DIVINE ? .2 : runtime.held == Held.RIFT ? 0 : 1;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED); if (speed == null) return;
        var old = speed.getModifier(SPEED); if (old != null && old.amount() == factor - 1) return;
        speed.removeModifier(SPEED); if (factor != 1) speed.addTransientModifier(new AttributeModifier(SPEED, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void clear(ServerPlayer player) {
        RUNTIME.remove(player); player.setAttached(STATE, State.EMPTY);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SPEED);
    }
    public static void clearAllRuntime() { RUNTIME.clear(); }
    private PillarWindActions() { }
}
