package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.player.Player;
import java.util.HashSet;
import java.util.Set;

/** Original HamonEnergySound: follows the user and fades by 0.1 after the hold ends. */
public final class VampireHamonSound extends AbstractTickableSoundInstance {
    private final Player player;
    private boolean fading;
    private static Object session;
    private static final Set<Integer> ACTIVE = new HashSet<>();
    private VampireHamonSound(Player player) {
        super(JojoSounds.get("hamon_concentration"), player.getSoundSource(), player.getRandom());
        this.player = player; volume = pitch = 1;
        x = player.getX(); y = player.getY(); z = player.getZ();
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (session != client.level) { ACTIVE.clear(); session = client.level; }
            if (client.level == null || client.isPaused()) return;
            Set<Integer> current = new HashSet<>();
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof Player player && player.getAttachedOrCreate(VampireHamon.CHARGE) > 0) {
                    current.add(player.getId());
                    if (ACTIVE.add(player.getId())) client.getSoundManager().play(new VampireHamonSound(player));
                }
            ACTIVE.retainAll(current);
        });
    }
    @Override public void tick() {
        if (!player.isAlive() || player.isRemoved()) { stop(); return; }
        x = player.getX(); y = player.getY(); z = player.getZ();
        fading |= player.getAttachedOrCreate(VampireHamon.CHARGE) == 0;
        if (fading) { volume = Math.max(volume - .1F, 0); if (volume == 0) stop(); }
        else if (player == Minecraft.getInstance().player) {
            var vampire = player.getAttachedOrCreate(VampireAttachments.PROGRESS);
            volume = vampire.blood() / vampire.maxBlood();
        }
    }
}
