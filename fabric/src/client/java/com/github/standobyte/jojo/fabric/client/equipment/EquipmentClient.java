package com.github.standobyte.jojo.fabric.client.equipment;

import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.equipment.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Isolated client bootstrap; sound instances stop on release, depletion, removal and world changes. */
public final class EquipmentClient {
    private static Object session;
    private static final Map<Integer, Loop> SOUNDS = new HashMap<>();
    public static void initialize() {
        EntityRendererRegistry.register(EquipmentContent.BLADE_HAT_ENTITY, BladeHatRenderer::new);
        EntityRendererRegistry.register(EquipmentContent.TOMMY_GUN_BULLET, TommyGunBulletRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (session != client.level) { SOUNDS.values().forEach(Loop::finish); SOUNDS.clear(); session = client.level; }
            if (client.level == null || client.isPaused()) return;
            var current = new HashSet<Integer>();
            for (var entity : client.level.entitiesForRendering()) {
                boolean hat = entity instanceof BladeHatEntity blade && !blade.isInGround();
                boolean gun = entity instanceof LivingEntity living && living.isUsingItem()
                        && living.getUseItem().is(EquipmentContent.TOMMY_GUN) && TommyGunItem.getAmmo(living.getUseItem()) > 0;
                if (entity.isAlive() && (hat || gun)) {
                    current.add(entity.getId());
                    if (!SOUNDS.containsKey(entity.getId())) {
                        var sound = new Loop(entity, hat); SOUNDS.put(entity.getId(), sound); client.getSoundManager().play(sound);
                    }
                }
            }
            SOUNDS.entrySet().removeIf(entry -> { if (current.contains(entry.getKey())) return false; entry.getValue().finish(); return true; });
        });
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Entity entity; private final boolean hat;
        Loop(Entity entity, boolean hat) {
            super(JojoSounds.get(hat ? "blade_hat_spinning" : "tommy_gun_loop"),entity.getSoundSource(),net.minecraft.util.RandomSource.create());
            this.entity=entity; this.hat=hat; looping=true; delay=0; volume=pitch=1; updatePosition();
        }
        void finish() { stop(); }
        private void updatePosition() { x=entity.getX(); y=entity.getY(); z=entity.getZ(); }
        @Override public boolean canPlaySound() { return !entity.isSilent(); }
        @Override public void tick() {
            if (Minecraft.getInstance().level != entity.level() || entity.isRemoved() || !entity.isAlive()) { stop(); return; }
            if (hat) {
                if (((BladeHatEntity)entity).isInGround()) { stop(); return; }
                volume=TimeStopClient.isFrozen(entity) ? 0 : (float)entity.getDeltaMovement().length();
            } else {
                var living=(LivingEntity)entity;
                if (!living.isUsingItem() || !living.getUseItem().is(EquipmentContent.TOMMY_GUN) || TommyGunItem.getAmmo(living.getUseItem()) <= 0) { stop(); return; }
            }
            updatePosition();
        }
    }
    private EquipmentClient() {}
}
