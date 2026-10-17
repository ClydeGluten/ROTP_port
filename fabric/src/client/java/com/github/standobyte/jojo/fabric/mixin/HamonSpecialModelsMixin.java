package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.client.HamonClackersRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SpecialModelRenderers.class)
abstract class HamonSpecialModelsMixin{
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper<Identifier,MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;
    @Inject(method="bootstrap",at=@At("TAIL"))
    private static void jojo$hamonModels(CallbackInfo ci){ID_MAPPER.put(Identifier.fromNamespaceAndPath("jojo","clackers"),HamonClackersRenderer.Unbaked.CODEC);ID_MAPPER.put(Identifier.fromNamespaceAndPath("jojo","tommy_gun"),com.github.standobyte.jojo.fabric.client.equipment.TommyGunRenderer.Unbaked.CODEC);}
}
