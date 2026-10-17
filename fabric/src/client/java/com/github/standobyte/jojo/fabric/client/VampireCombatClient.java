package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.VampireCombatService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

/** Source freeze cloud emitted around every tracked holder, independent of HUD selection. */
public final class VampireCombatClient {
    public static void initialize() {
        EntityRendererRegistry.register(VampireCombatService.EYES, SpaceRipperStingyEyesRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.isPaused()) return;
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof Player player) || !VampireCombatService.state(player).freeze() || TimeStopClient.isFrozen(player)) continue;
                var random = player.getRandom();
                client.level.addParticle(ParticleTypes.CLOUD,
                        player.getX() + (random.nextFloat() - .5) * (player.getBbWidth() + 1),
                        player.getY() + random.nextFloat() * (player.getBbHeight() + 1),
                        player.getZ() + (random.nextFloat() - .5) * (player.getBbWidth() + 1), 0, 0, 0);
            }
        });
    }
    private VampireCombatClient() { }
}
