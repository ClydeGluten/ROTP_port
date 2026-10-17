package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireBloodParticles;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.pillarman.PillarWindActions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Original particle-only storm renderer, source particle frames, and WIND cloak body/hand layer. */
public final class PillarWindClient {
    private static final Identifier CLOAK_TEXTURE = RipplesFabric.id("textures/entity/biped/wind_cloak.png");
    private static final Map<LivingEntity, PillarWindActions.Held> PREVIOUS = new WeakHashMap<>();
    private record View(boolean cloak, float age, boolean spectator) { }
    private static final Map<AvatarRenderState, View> VIEWS = new WeakHashMap<>();
    private static SpriteSet auraSprites;
    private static boolean initialized;
    public static void initialize() {
        if (initialized) return; initialized = true;
        EntityRendererRegistry.register(PillarWindActions.SANDSTORM_ENTITY, NoopRenderer::new);
        ParticleProviderRegistry.getInstance().register(PillarWindActions.SANDSTORM, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new StormParticle(level, x, y, z, dx, false, sprites));
        ParticleProviderRegistry.getInstance().register(PillarWindActions.RIFT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new StormParticle(level, x, y, z, dx, true, sprites));
        ParticleProviderRegistry.getInstance().register(PillarWindActions.GREEN_AURA, sprites -> {
            auraSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz, random) -> new GreenAura(level, x, y, z, sprites, null);
        });
        ClientTickEvents.END_CLIENT_TICK.register(PillarWindClient::tick);
    }
    public static void send(PillarWindActions.Action action) {
        if (ClientPlayNetworking.canSend(PillarWindActions.Intent.TYPE)) ClientPlayNetworking.send(new PillarWindActions.Intent(action));
    }
    public static float movementMultiplier(LivingEntity player) {
        return switch (PillarWindActions.state(player).held()) { case DIVINE -> .2F; case RIFT -> 0; default -> 1; };
    }
    /** 05-34 uses the source clip IDs rather than inventing a new static action pose. */
    public static String animation(LivingEntity player) {
        return switch (PillarWindActions.state(player).held()) { case DIVINE -> "pillarman/divine_sandstorm"; case RIFT -> "pillarman/atmospheric_rift"; default -> ""; };
    }
    /** Original integer intensity/5 yields one pseudo-particle per hand for Divine, zero for Rift. */
    public static int firstPersonAuraCount(LivingEntity player) {
        var state = PillarWindActions.state(player); return state.held() == PillarWindActions.Held.DIVINE && state.ticks() < 40 ? 1 : 0;
    }
    private static void tick(Minecraft client) {
        if (client.level == null) { PREVIOUS.clear(); VIEWS.clear(); return; }
        if (client.isPaused()) return;
        PREVIOUS.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != client.level);
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || TimeStopClient.isFrozen(living)) continue;
            var state = PillarWindActions.state(living); var previous = PREVIOUS.getOrDefault(living, PillarWindActions.Held.NONE);
            if ((previous == PillarWindActions.Held.CLOAK) != (state.held() == PillarWindActions.Held.CLOAK)) cloakBurst(living);
            if (state.held() == PillarWindActions.Held.NONE) PREVIOUS.remove(living); else PREVIOUS.put(living, state.held());
            boolean divine = state.held() == PillarWindActions.Held.DIVINE, rift = state.held() == PillarWindActions.Held.RIFT;
            if ((divine || rift) && state.ticks() < 40 && auraSprites != null) {
                // Source attached third-person aura is hidden from its owner's first-person camera.
                if (client.getCameraEntity() != living || !client.options.getCameraType().isFirstPerson()) {
                    for (int i = 0; i < (divine ? 6 : 3); i++) {
                        var random = living.getRandom();
                        client.particleEngine.add(new GreenAura(client.level,
                                living.getX() + (random.nextDouble() - .5) * (living.getBbWidth() + .5),
                                living.getY() + random.nextDouble() * living.getBbHeight() * .5,
                                living.getZ() + (random.nextDouble() - .5) * (living.getBbWidth() + .5), auraSprites, living));
                    }
                }
            } else if (rift && state.ticks() >= 40) {
                for (int i = 0; i < 3; i++) {
                    var random = living.getRandom();
                    client.level.addParticle(VampireBloodParticles.BLOOD,
                            living.getX() + (random.nextDouble() - .5) * (living.getBbWidth() + .5), living.getY() + random.nextDouble() * living.getBbHeight(),
                            living.getZ() + (random.nextDouble() - .5) * (living.getBbWidth() + .5),
                            (random.nextDouble() - .5) / 2, (random.nextDouble() - .5) / 2, (random.nextDouble() - .5) / 2);
                }
            }
        }
    }
    private static void cloakBurst(LivingEntity user) {
        for (int i = 0; i < 15; i++) {
            var random = user.getRandom();
            user.level().addParticle(PillarWindActions.SANDSTORM,
                    user.getX() + (random.nextDouble() - .5) * (user.getBbWidth() + .5), user.getY() + random.nextDouble() * user.getBbHeight(),
                    user.getZ() + (random.nextDouble() - .5) * (user.getBbWidth() + .5), random.nextDouble() - .5, random.nextDouble(), random.nextDouble() - .5);
        }
    }
    public static void extract(Avatar player, AvatarRenderState state, float partial) {
        VIEWS.put(state, new View(PillarWindActions.state(player).held() == PillarWindActions.Held.CLOAK,
                player.tickCount + (TimeStopClient.isFrozen(player) ? 0 : partial), player.isSpectator()));
    }
    public static final class CloakLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final PlayerModel model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(.5F), false), 64, 64).bakeRoot(), false);
        public CloakLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }
        @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
            var view = VIEWS.get(state); if (view == null || !view.cloak || view.spectator) return;
            // Source intentionally renders the cloak while its user has Invisibility.
            collector.submitModel(model, state, pose, RenderTypes.energySwirl(CLOAK_TEXTURE, view.age * .01F, view.age * .01F), light, OverlayTexture.NO_OVERLAY, 0x40333333);
        }
    }
    public static void submitHand(PoseStack pose, SubmitNodeCollector collector, ModelPart arm, ModelPart outer, int light) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || PillarWindActions.state(player).held() != PillarWindActions.Held.CLOAK) return;
        float armPitch = arm.xRot, outerPitch = outer.xRot;
        arm.xRot = 0; outer.xRot = 0;
        var type = RenderTypes.energySwirl(CLOAK_TEXTURE, player.tickCount * .01F, player.tickCount * .01F);
        collector.submitModelPart(arm, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0x40333333);
        collector.submitModelPart(outer, pose, type, light, OverlayTexture.NO_OVERLAY, null, 0x40333333);
        arm.xRot = armPitch; outer.xRot = outerPitch;
    }
    private static final class StormParticle extends SingleQuadParticle {
        private final SpriteSet sprites;
        StormParticle(ClientLevel level, double x, double y, double z, double size, boolean atmospheric, SpriteSet sprites) {
            super(level, x, y, z, sprites.get(level.getRandom())); this.sprites = sprites;
            lifetime = 6 + random.nextInt(4); float color = random.nextFloat() * .6F + .4F; setColor(color, color, color);
            quadSize = atmospheric ? .7F : 2 * (1 - (float) size * .5F); alpha = .2F; setSprite(sprites.get(0, lifetime));
        }
        @Override public void tick() { xo = x; yo = y; zo = z; if (age++ >= lifetime) remove(); else setSprite(sprites.get(age, lifetime)); }
        @Override protected int getLightCoords(float partial) { return 0xF000F0; }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
    private static final class GreenAura extends SingleQuadParticle {
        private final SpriteSet sprites; private final int offset; private final LivingEntity owner; private Vec3 lastOwnerPosition;
        GreenAura(ClientLevel level, double x, double y, double z, SpriteSet sprites, LivingEntity owner) {
            super(level, x, y, z, 0, 0, 0, sprites.get(level.getRandom())); this.sprites = sprites; this.owner = owner;
            if (owner != null) lastOwnerPosition = owner.position();
            xd *= .1; yd = yd * .1 + .004; zd *= .1; friction = .96F; hasPhysics = false;
            quadSize *= .3F * (1.2F + .6F * random.nextFloat()); lifetime = 25 + random.nextInt(10); offset = random.nextInt(lifetime); alpha = .25F;
        }
        @Override public void tick() {
            super.tick(); float a = (float) age / lifetime; alpha = .05F + (a <= .5F ? a * 2 : (1 - a) * 2) * .3F;
            setSprite(sprites.get((age + offset) % lifetime, lifetime));
            if (owner != null) { Vec3 delta = owner.position().subtract(lastOwnerPosition); move(delta.x, delta.y, delta.z); lastOwnerPosition = owner.position(); }
        }
        @Override protected int getLightCoords(float partial) { return 0xF000F0; }
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
    private PillarWindClient() { }
}
