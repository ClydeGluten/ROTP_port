package com.github.standobyte.jojo.fabric.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/** ClientSetup's original jojo:count predicate: bundles appear only when a living entity owns the render. */
public record KnifeCountProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<KnifeCountProperty> MAP_CODEC = MapCodec.unit(new KnifeCountProperty());

    @Override public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return owner != null && owner.asLivingEntity() != null ? stack.getCount() : 1;
    }

    @Override public MapCodec<KnifeCountProperty> type() { return MAP_CODEC; }
}
