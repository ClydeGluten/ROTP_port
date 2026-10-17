package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandManifestation;
import com.github.standobyte.jojo.fabric.SpeciesHooks;
import com.github.standobyte.jojo.fabric.TimeStopService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source sensory overload, repeated-hit resistance and the delayed owner-only consciousness impulse. */
public final class GELifeshotState {
    public record Resistance(int ticks, float amount) {
        public static final Resistance EMPTY = new Resistance(0, 0);
        public static final Codec<Resistance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 1200).optionalFieldOf("LifeShotTicks", 0).forGetter(Resistance::ticks),
                Codec.floatRange(0, 100).optionalFieldOf("LifeShotResist", 0F).forGetter(Resistance::amount)).apply(i, Resistance::new));
    }
    public static final AttachmentType<Resistance> RESISTANCE = AttachmentRegistry.<Resistance>builder().initializer(() -> Resistance.EMPTY)
            .persistent(Resistance.CODEC).buildAndRegister(RipplesFabric.id("ge_lifeshot_resistance"));
    public interface CreeperSwell { void jojo$setGECreeperSwell(int value); }
    public interface ZombieConversion { void jojo$startGEConversion(UUID player, int ticks); }
    private static final Map<ServerPlayer, Integer> SEND_IMPULSE = new WeakHashMap<>();
    private static final Map<Player, Vec3> CLIENT_IMPULSE = new WeakHashMap<>();
    public static final Holder<MobEffect> SENSORY_OVERLOAD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("sensory_overload"), new MobEffect(MobEffectCategory.HARMFUL, 0xD88F1F) {
                @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
                @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
                    if (entity instanceof Creeper creeper) ((CreeperSwell) creeper).jojo$setGECreeperSwell(-1);
                    return true;
                }
                @Override public void onEffectStarted(LivingEntity entity, int amplifier) { started(entity); }
            });
    public static boolean active(LivingEntity entity) { return entity.hasEffect(SENSORY_OVERLOAD); }
    public static boolean applicable(LivingEntity entity) {
        return !(entity instanceof Player player && player.getAbilities().instabuild) && !(entity instanceof Mob mob && mob.isNoAi());
    }
    public static void started(LivingEntity entity) {
        if (entity instanceof Mob mob) mob.setNoAi(true);
        if (entity instanceof ServerPlayer player) SEND_IMPULSE.put(player, 2);
    }
    /** Root effect-removal hook calls this for every removal path; the original resets NoAI to false. */
    public static void removed(LivingEntity entity) {
        if (entity instanceof Mob mob) mob.setNoAi(false);
        if (entity instanceof ServerPlayer player) SEND_IMPULSE.remove(player);
    }
    public static boolean apply(ServerPlayer owner, LivingEntity target) {
        if (target instanceof StandManifestation stand) target = stand.owner();
        if (target == null || target.isRemoved()) return false;
        if (target.typeHolder().is(EntityTypeTags.UNDEAD) || SpeciesHooks.undeadPlayer(target)) {
            if (target instanceof ZombieVillager zombie) {
                ((ZombieConversion) zombie).jojo$startGEConversion(owner.getUUID(), target.getRandom().nextInt(2401) + 3600); return true;
            }
            return false;
        }
        if (active(target)) return false;
        giveEffectWithResist(target); return true;
    }
    public static int giveEffectWithResist(LivingEntity target) {
        Resistance previous = target.getAttachedOrCreate(RESISTANCE);
        float resistance = previous.ticks > 0 ? Math.min(previous.amount + 40, 100) : previous.amount > 0 ? Math.min(previous.amount + 20, 100) : 0;
        target.setAttached(RESISTANCE, new Resistance(1200, resistance));
        int duration = 100 - (int) resistance;
        if (duration > 0) {
            if (applicable(target)) target.addEffect(new MobEffectInstance(SENSORY_OVERLOAD, duration, 0, false, false, false));
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.HASTE, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, false));
        }
        return duration;
    }
    /** One call per actual living tick. Resistance decays after the source sixty-second reset interval. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide() || TimeStopService.isFrozen(entity)) return;
        Resistance before = entity.getAttached(RESISTANCE);
        if (before != null && (before.ticks > 0 || before.amount > 0)) {
            int ticks = Math.max(before.ticks - 1, 0); float amount = ticks == 0 ? Math.max(before.amount - .125F, 0) : before.amount;
            entity.setAttached(RESISTANCE, new Resistance(ticks, amount));
        }
        if (entity instanceof ServerPlayer player && SEND_IMPULSE.containsKey(player)) {
            int ticks = SEND_IMPULSE.get(player) - 1;
            if (ticks <= 0) {
                SEND_IMPULSE.remove(player);
                if (active(player) && ServerPlayNetworking.canSend(player, Impulse.TYPE)) ServerPlayNetworking.send(player,
                        new Impulse(player.getUUID(), player.level().dimension().identifier(), player.getDeltaMovement()));
            } else SEND_IMPULSE.put(player, ticks);
        }
    }
    public record Impulse(UUID player, Identifier dimension, Vec3 motion) implements CustomPacketPayload {
        public static final Type<Impulse> TYPE = new Type<>(RipplesFabric.id("ge_split_consciousness"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Impulse> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.player); b.writeUtf(p.dimension.toString(), 256); b.writeDouble(p.motion.x); b.writeDouble(p.motion.y); b.writeDouble(p.motion.z);
        }, b -> {
            UUID player = b.readUUID(); Identifier dimension = Identifier.tryParse(b.readUtf(256)); Vec3 motion = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
            if (dimension == null || !Double.isFinite(motion.lengthSqr())) throw new DecoderException("Invalid consciousness impulse");
            return new Impulse(player, dimension, motion);
        });
        @Override public Type<Impulse> type() { return TYPE; }
    }
    public static void receive(Player player, Impulse payload) {
        if (player != null && active(player) && player.getUUID().equals(payload.player) && player.level().dimension().identifier().equals(payload.dimension)) CLIENT_IMPULSE.put(player, payload.motion);
    }
    public static Optional<Vec3> takeImpulse(Player player) { return Optional.ofNullable(CLIENT_IMPULSE.remove(player)); }
    public static void clearRuntime() { SEND_IMPULSE.clear(); CLIENT_IMPULSE.clear(); }
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(Impulse.TYPE, Impulse.CODEC); }
    private GELifeshotState() { }
}
