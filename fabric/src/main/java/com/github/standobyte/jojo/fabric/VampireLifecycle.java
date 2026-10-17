package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;

/** Original VampirismData curing and WoodenCoffinBlock sleep/respawn rules. */
public final class VampireLifecycle {
    public static int curingTicks(Player player) { return player.getAttachedOrCreate(VampireAttachments.CURING); }
    public static int duration() { return VampireConfig.server().vampirismCuringDuration(); }
    public static float cureProgress(Player player) { return Math.min((float) curingTicks(player) / VampireConfig.current(player.level().isClientSide()).vampirismCuringDuration(), 1); }
    public static int curingStage(Player player) { return curingTicks(player) > 0 ? (int) Math.clamp(cureProgress(player) * 4, 0, 3) + 1 : 0; }
    public static void initialize() {
        EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) -> !(player.level().getBlockState(pos).getBlock() instanceof WoodenCoffinBlock) || VampireService.progress(player).vampire());
        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((player, pos, vanilla) -> player.level().getBlockState(pos).getBlock() instanceof WoodenCoffinBlock ? EventResult.ALLOW : EventResult.PASS);
        EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity, pos, state, occupied) -> {
            if (!(state.getBlock() instanceof WoodenCoffinBlock)) return false;
            entity.level().setBlock(pos, state.setValue(WoodenCoffinBlock.OCCUPIED, occupied).setValue(WoodenCoffinBlock.CLOSED, occupied), 3);
            return true;
        });
        EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
            var state = entity.level().getBlockState(pos);
            if (state.getBlock() instanceof WoodenCoffinBlock) {
                var facing = state.getValue(WoodenCoffinBlock.FACING);
                entity.setPos(pos.getX() + .5 + facing.getStepX() * .085, pos.getY() + .1875, pos.getZ() + .5 + facing.getStepZ() * .085);
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> { if (entity instanceof ServerPlayer player) { player.setAttached(VampireAttachments.COFFIN_RESPAWN, false); finishCuring(player); } });
    }
    public static void onEnchantedApple(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !VampireService.progress(player).vampire()) return;
        var weakness = entity.getEffect(MobEffects.WEAKNESS);
        if (weakness == null || weakness.getAmplifier() < 4) return;
        player.setAttached(VampireAttachments.CURING, 1);
        if (!player.isSilent()) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get("vampire_cure_start"), player.getSoundSource(), 1, 1);
    }
    public static void tick(ServerPlayer player) {
        int ticks = curingTicks(player);
        if (ticks <= 0) return;
        player.setYRot(player.getYRot() + (float) (Math.cos(player.tickCount * 3.25) * Math.PI * .4));
        // Preserve the original integer-division chance and RNG call.
        if (curingStage(player) >= 2 && player.getRandom().nextDouble() <= 0) player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
        if (ticks >= duration()) player.sendOverlayMessage(Component.translatable("jojo.vampire.ready_to_cure"));
        else if (VampireService.progress(player).blood() == 0) {
            int advance = 1;
            if (player.getRandom().nextFloat() < .01F) {
                int accelerators = 0;
                BlockPos center = player.blockPosition();
                for (int x = -4; x < 4; x++) for (int y = -4; y < 4; y++) for (int z = -4; z < 4; z++) {
                    var block = player.level().getBlockState(center.offset(x, y, z)).getBlock();
                    if (block == Blocks.IRON_BARS || block instanceof BedBlock) {
                        if (player.getRandom().nextFloat() < .3F) advance++;
                        if (++accelerators >= 14) break;
                    }
                }
            }
            player.setAttached(VampireAttachments.CURING, Math.min(ticks + advance, duration()));
        }
    }
    public static void finishCuring(ServerPlayer player) {
        if (!VampireService.progress(player).vampire() || curingTicks(player) < duration()) return;
        if (!player.isSilent()) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get("vampire_cure_end"), player.getSoundSource(), 1, 1);
        VampireService.clear(player);
        VampireAdvancements.onCured(player);
        player.getFoodData().setFoodLevel(1);
        player.removeEffect(VampireItems.SUN_BURN);
        player.removeEffect(MobEffects.WEAKNESS);
        for (var effect : java.util.List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS)) player.addEffect(new MobEffectInstance(effect, 600, 1));
    }
    public static int passiveAmplifier(Player player, Holder<MobEffect> effect) {
        int difficulty = player.level().getDifficulty().getId(), blood = VampireService.progress(player).bloodLevel(difficulty), stage = curingStage(player);
        if (stage > 0) blood = stage >= 3 ? -1 : blood - stage;
        if (effect == MobEffects.HEALTH_BOOST) return difficulty * (stage > 0 ? 5 - stage * 2 : 5) - 1;
        if (effect == MobEffects.REGENERATION) return Math.min(blood - 2, 4);
        if (effect == MobEffects.STRENGTH) return blood - 5;
        if (effect == MobEffects.SPEED || effect == MobEffects.HASTE || effect == MobEffects.JUMP_BOOST) return blood - 4;
        if (effect == MobEffects.NIGHT_VISION) return 0;
        if (effect == MobEffects.BLINDNESS) return stage >= 4 ? 0 : -1;
        return stage >= 4 ? 3 - difficulty : -1;
    }
    public static boolean preventRespawnTimeSkip(Player player) {
        var level = player.level();
        boolean sunny = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && level.isBrightOutside() && !level.isRaining() && !level.isThundering();
        return player.getAttachedOrCreate(VampireAttachments.COFFIN_RESPAWN) && isSleepingInCoffin(player) && !sunny;
    }
    public static boolean isSleepingInCoffin(LivingEntity entity) {
        return entity.getSleepingPos().map(pos -> entity.level().getBlockState(pos).getBlock() instanceof WoodenCoffinBlock).orElse(false);
    }
    public static ServerClockManager.MoveResult sleepToNight(ServerLevel level, ServerClockManager clocks, Holder<WorldClock> clock) {
        long coffins = level.players().stream().filter(player -> player.isSleeping() && isSleepingInCoffin(player)).count();
        for (var player : level.players()) if (player.isSleeping() && isSleepingInCoffin(player)) {
            if (player.hasEffect(VampireItems.SUN_BURN)) { player.removeEffect(VampireItems.SUN_BURN); player.removeEffect(MobEffects.WEAKNESS); }
        }
        if (coffins < (level.players().size() + 1) / 2 || coffins == 0) return null;
        long time = clocks.getInstance(clock).totalTicks();
        long added = (24000 - time % 24000 + 12600) % 24000;
        clocks.setTotalTicks(clock, time + added);
        long oldDayTime = time % 24000;
        if (oldDayTime < 12600 || oldDayTime >= 23500) {
            for (var player : level.players()) if (player.isSleeping() && isSleepingInCoffin(player)) VampireAdvancements.onCoffinSleep(player);
        }
        return added == 0 ? ServerClockManager.MoveResult.NOT_MOVED : ServerClockManager.MoveResult.MOVED;
    }
    public static void onRespawn(ServerPlayer player) {
        if (!VampireService.progress(player).vampire()) { player.setAttached(VampireAttachments.CURING, 0); return; }
        var spawn = player.getRespawnConfig();
        if (spawn == null || !spawn.respawnData().dimension().equals(player.level().dimension())) return;
        var pos = spawn.respawnData().pos();
        var state = player.level().getBlockState(pos);
        if (state.getBlock() instanceof WoodenCoffinBlock coffin) {
            coffin.sleepInside(player.level(), pos, state, player);
            if (player.isSleeping()) player.setAttached(VampireAttachments.COFFIN_RESPAWN, true);
        }
    }
    private VampireLifecycle() { }
}
