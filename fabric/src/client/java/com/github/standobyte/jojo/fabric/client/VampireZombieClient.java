package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireZombieService;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** Original zombie geometry and armor layers with the retained hungry-zombie texture. */
public final class VampireZombieClient {
    public static void initialize() { EntityRendererRegistry.register(VampireZombieService.HUNGRY_ZOMBIE, Renderer::new); }
    private static final class Renderer extends ZombieRenderer {
        private static final Identifier TEXTURE = RipplesFabric.id("textures/entity/biped/hungry_zombie.png");
        Renderer(EntityRendererProvider.Context context) { super(context); }
        @Override public Identifier getTextureLocation(ZombieRenderState state) { return TEXTURE; }
    }
    private VampireZombieClient() { }
}
