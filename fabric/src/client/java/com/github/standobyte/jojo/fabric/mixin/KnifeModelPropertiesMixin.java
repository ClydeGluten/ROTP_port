package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.KnifeCountProperty;
import com.github.standobyte.jojo.fabric.client.StoneMaskActivationProperty;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register alongside vanilla properties before the first item-model resource load. */
@Mixin(RangeSelectItemModelProperties.class)
public abstract class KnifeModelPropertiesMixin {
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper<Identifier,
            MapCodec<? extends RangeSelectItemModelProperty>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void jojoRegisterKnifeCount(CallbackInfo ci) {
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("jojo", "count"), KnifeCountProperty.MAP_CODEC);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("jojo", "stone_mask_activated"), StoneMaskActivationProperty.MAP_CODEC);
    }
}
