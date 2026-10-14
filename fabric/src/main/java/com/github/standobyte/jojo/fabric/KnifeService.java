package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** TheWorldEntity.onKnivesThrow and PlayerUtilCap's short-lived voice-line counter. */
public final class KnifeService {
    private static final AttachmentType<Integer> THREW_TICKS = AttachmentRegistry.<Integer>builder()
            .initializer(() -> 0).buildAndRegister(RipplesFabric.id("knives_threw_ticks"));
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                int remaining = player.getAttachedOrCreate(THREW_TICKS);
                if (remaining > 0 && !TimeStopService.isFrozen(player)) player.setAttached(THREW_TICKS, remaining - 1);
            }
        });
    }
    static void onKnivesThrow(ServerPlayer player, StarPlatinumEntity stand, ItemStack stack, int thrown) {
        if (thrown == 1 && player.getAttachedOrCreate(THREW_TICKS) > 0) {
            voice(player, "dio_one_more");
            player.setAttached(THREW_TICKS, 0);
        }
        if (thrown > 1 && TimeStopService.isTimeStopped(player.level(), player.chunkPosition())) player.setAttached(THREW_TICKS, 80);
        if (!player.isShiftKeyDown() && stack.getCount() > 0) {
            int count = Math.min(stack.getCount(), KnifeItem.MAX_KNIVES_THROW);
            KnifeItem.throwVolley(player.level(), player, count, stand);
            KnifeItem.playThrowSound(player.level(), stand.getX(), stand.getY(), stand.getZ(), count, 0.5F);
            if (!player.getAbilities().instabuild) stack.shrink(count);
            int remaining = TimeStopService.ticksRemaining(player);
            if (TimeStopService.durationTicks(player) == 100 && remaining > 50 && remaining <= 80) voice(player, "dio_5_seconds");
        }
    }
    private static void voice(ServerPlayer player, String sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), JojoSounds.get(sound), SoundSource.PLAYERS, 1F, 1F);
    }
    private KnifeService() { }
}
