package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
public final class HamonContentClient {
    public static void initialize(){
        EntityRendererRegistry.register(HamonContentEntities.MOLOTOV,HamonMolotovRenderer::new);
        EntityRendererRegistry.register(HamonContentEntities.HAMON_MASTER,HamonMasterRenderer::new);
        EntityRendererRegistry.register(HamonContentEntities.LEAVES_GLIDER,context->new HamonContentRenderer<>(context,"glider"));
        EntityRendererRegistry.register(HamonContentEntities.CLACKERS,context->new HamonContentRenderer<>(context,"clackers"));
        EntityRendererRegistry.register(HamonContentEntities.CRIMSON_BUBBLE,context->new HamonContentRenderer<>(context,"crimson"));
    }
    private HamonContentClient(){}
}
