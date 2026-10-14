package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.KnifeItem;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.apache.commons.lang3.function.TriConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BiConsumer;

/** Forge's stack-aware attribute hook became a component iterator in modern Minecraft. */
@Mixin(ItemStack.class)
public abstract class KnifeAttributesMixin {
    private boolean jojo$isSingleKnife() {
        ItemStack stack = (ItemStack) (Object) this;
        return stack.getItem() instanceof KnifeItem && stack.getCount() == 1;
    }
    private static AttributeModifier jojo$knifeDamage() {
        return new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 2, AttributeModifier.Operation.ADD_VALUE);
    }
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("TAIL"))
    private void jojo$knifeEquipment(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
        if (slot == EquipmentSlot.MAINHAND && jojo$isSingleKnife()) consumer.accept(Attributes.ATTACK_DAMAGE, jojo$knifeDamage());
    }
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V", at = @At("TAIL"))
    private void jojo$knifeTooltip(EquipmentSlotGroup slot, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> consumer, CallbackInfo ci) {
        if (slot == EquipmentSlotGroup.MAINHAND && jojo$isSingleKnife()) consumer.accept(Attributes.ATTACK_DAMAGE, jojo$knifeDamage(), ItemAttributeModifiers.Display.attributeModifiers());
    }
}
