package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StoneMaskItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/** Modern item-model adapter for ClientSetup's original jojo:stone_mask_activated predicate. */
public record StoneMaskActivationProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<StoneMaskActivationProperty> MAP_CODEC = MapCodec.unit(new StoneMaskActivationProperty());

    @Override public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return StoneMaskItem.activationTicks(stack) > 0 ? 1 : 0;
    }

    @Override public MapCodec<StoneMaskActivationProperty> type() { return MAP_CODEC; }
}
