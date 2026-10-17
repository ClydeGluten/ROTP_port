package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.pillarman.PillarLightActions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/** Original LIGHT sprites, flash expansion, charge aura and tracking emitters. Shared Pillar presentation owns poses/blades. */
public final class PillarLightClient {
    private static SpriteSet rainbow;
    private static boolean initialized;
    private static boolean forcingSwing;
    /** Client SwingState mixin bypasses startIfAble only during this original forced barrage swing. */
    public static boolean forcingSwing() { return forcingSwing; }
    public static void initialize() {
        if (initialized) return; initialized = true;
        ParticleProviderRegistry.getInstance().register(PillarLightActions.LIGHT_SPARK, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new Glint(level, x, y, z, dx, dy, dz, sprites, false, null));
        ParticleProviderRegistry.getInstance().register(PillarLightActions.LIGHT_MODE_FLASH, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new Flash(level, x, y, z, sprites));
        ParticleProviderRegistry.getInstance().register(PillarLightActions.RAINBOW_AURA, sprites -> {
            rainbow = sprites;
            return (type, level, x, y, z, dx, dy, dz, random) -> new Glint(level, x, y, z, dx, dy, dz, sprites, true, null);
        });
        ClientPlayNetworking.registerGlobalReceiver(PillarLightActions.Emitter.TYPE, (packet, context) -> {
            var client = context.client(); if (client.level == null) return;
            var entity = client.level.getEntity(packet.entityId()); if (entity == null) return;
            if (packet.count() == 0) {
                for (int i = 19; i >= 0; i--) client.particleEngine.createTrackingEmitter(entity,
                        packet.decoy() ? net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 1F, 1F, 1F)
                                : PillarLightActions.LIGHT_MODE_FLASH, Math.max(1, 19 - i));
            } else for (int i = 0; i <= packet.count(); i++) {
                var random = entity.getRandom();
                client.level.addParticle(PillarLightActions.LIGHT_SPARK, entity.getX(), entity.getY() + (entity instanceof LivingEntity ? 1 : 0), entity.getZ(),
                        (random.nextDouble() - .5) / 3, (random.nextDouble() - .5) / 3, (random.nextDouble() - .5) / 3);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(PillarLightClient::tick);
    }
    /** Source first-person aura intensity10 /5 = two sprites per hand. Shared hand hook supplies original positioning. */
    public static int firstPersonAuraCount(LivingEntity user) { return PillarLightActions.state(user).phase() == PillarLightActions.Phase.DECOY ? 2 : 0; }
    public static String animation(LivingEntity user) {
        return switch (PillarLightActions.state(user).phase()) {
            case FLASH -> "pillarman/light_flash"; case DECOY -> "pillarman/light_flash_decoy";
            case SLASH -> "pillarman/blade_slash"; case DASH_CHARGE, DASH -> "pillarman/blade_dash";
            case BARRAGE -> "pillarman/blade_barrage"; default -> "";
        };
    }
    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused()) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity user) || TimeStopClient.isFrozen(user)) continue;
            var state = PillarLightActions.state(user); var random = user.getRandom();
            if (state.phase() == PillarLightActions.Phase.FLASH && state.ticks() > 10) {
                for (int i = 0; i <= 24; i++) client.level.addParticle(PillarLightActions.LIGHT_SPARK,
                        user.getX(), user.getY() + .8, user.getZ(), (random.nextDouble() - .5) / 4, (random.nextDouble() - .5) / 4, (random.nextDouble() - .5) / 4);
            }
            if (state.phase() == PillarLightActions.Phase.DECOY && rainbow != null
                    && (client.getCameraEntity() != user || !client.options.getCameraType().isFirstPerson())) {
                for (int i = 0; i < 10; i++) client.particleEngine.add(new Glint(client.level,
                        user.getX() + (random.nextDouble() - .5) * (user.getBbWidth() + .5), user.getY() + random.nextDouble() * user.getBbHeight() * .5,
                        user.getZ() + (random.nextDouble() - .5) * (user.getBbWidth() + .5), 0, 0, 0, rainbow, true, user));
            }
            if (state.phase() == PillarLightActions.Phase.BARRAGE && state.ticks() % 2 == 0) {
                forcingSwing = true;
                try { user.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true); }
                finally { forcingSwing = false; }
            }
        }
    }
    private static final class Glint extends SingleQuadParticle {
        private final SpriteSet sprites; private final int offset; private final LivingEntity owner; private Vec3 previous;
        Glint(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean aura, LivingEntity owner) {
            super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
            this.sprites = sprites; this.owner = owner; if (owner != null) previous = owner.position();
            xd = xd * .1 + dx; yd = yd * .1 + dy + .004; zd = zd * .1 + dz;
            friction = .96F; hasPhysics = false; quadSize *= .3F * (1.2F + .6F * random.nextFloat());
            lifetime = (aura ? 25 : 8) + random.nextInt(10); offset = random.nextInt(lifetime); alpha = .25F;
        }
        @Override public void tick() {
            super.tick(); setSprite(sprites.get((age + offset) % lifetime, lifetime));
            if (owner != null) { Vec3 delta = owner.position().subtract(previous); move(delta.x, delta.y, delta.z); previous = owner.position(); }
        }
        @Override public void extract(QuadParticleRenderState state, Camera camera, float partial) {
            float fraction = (age + partial) / lifetime; alpha = .05F + (fraction <= .5F ? fraction * 2 : (1 - fraction) * 2) * .3F;
            super.extract(state, camera, partial);
        }
        @Override protected int getLightCoords(float partial) { return 0xF000F0; }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
    private static final class Flash extends SingleQuadParticle {
        Flash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, sprites.get(level.getRandom())); lifetime = 4;
        }
        @Override public void extract(QuadParticleRenderState state, Camera camera, float partial) {
            alpha = .6F - (age + partial - 1) * .25F * .5F; super.extract(state, camera, partial);
        }
        @Override public float getQuadSize(float partial) { return 7.1F * (float) Math.sin((age + partial - 1) * .25F * Math.PI); }
        @Override protected int getLightCoords(float partial) { return 0xF000F0; }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
    private PillarLightClient() { }
}
