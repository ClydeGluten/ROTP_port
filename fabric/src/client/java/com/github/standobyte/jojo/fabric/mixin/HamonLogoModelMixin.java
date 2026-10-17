package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.HamonLogoRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 has no public special-item-renderer registration API. Register alongside vanilla renderer codecs. */
@Mixin(SpecialModelRenderers.class)
abstract class HamonLogoModelMixin {
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper<Identifier,MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;
    @Inject(method="bootstrap",at=@At("TAIL"))
    private static void jojo$originalLogo(CallbackInfo ci) {ID_MAPPER.put(RipplesFabric.id("mod_logo"),HamonLogoRenderer.Unbaked.MAP_CODEC);}
}
