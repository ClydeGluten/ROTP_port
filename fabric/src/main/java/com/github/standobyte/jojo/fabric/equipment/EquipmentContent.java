package com.github.standobyte.jojo.fabric.equipment;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetCustomDataFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Registration and lifecycle entrypoint for Blade Hat, Tommy Gun, and original paintings. */
public final class EquipmentContent {
    private static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> builder) {
        var key = ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static final EntityType<BladeHatEntity> BLADE_HAT_ENTITY = registerEntity("blade_hat",
            EntityType.Builder.<BladeHatEntity>of(BladeHatEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.375F)
                    .clientTrackingRange(4)
                    .updateInterval(20));

    public static final EntityType<TommyGunBulletEntity> TOMMY_GUN_BULLET = registerEntity("tommy_gun_bullet",
            EntityType.Builder.<TommyGunBulletEntity>of(TommyGunBulletEntity::new, MobCategory.MISC)
                    .sized(0.0625F, 0.0625F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune());

    public static final BladeHatItem BLADE_HAT;
    public static final TommyGunItem TOMMY_GUN;

    public static final ResourceKey<PaintingVariant> MONA_LISA_PAINTING =
            ResourceKey.create(Registries.PAINTING_VARIANT, RipplesFabric.id("mona_lisa"));
    public static final ResourceKey<PaintingVariant> HANDS_PAINTING =
            ResourceKey.create(Registries.PAINTING_VARIANT, RipplesFabric.id("hands"));

    static {
        var bladeHatKey = ResourceKey.create(Registries.ITEM, RipplesFabric.id("blade_hat"));
        // Original ModArmorMaterials.BLACK_CLOTH: maxDamageFactor=12 -> head durability 11*12=132,
        // defense=1, enchantability=15, equipSound=ARMOR_EQUIP_GENERIC, repair=BLACK_WOOL.
        BLADE_HAT = Registry.register(BuiltInRegistries.ITEM, bladeHatKey, new BladeHatItem(
                new Item.Properties()
                        .setId(bladeHatKey)
                        .durability(132)
                        .equippable(EquipmentSlot.HEAD)
                        .enchantable(15)
                        .repairable(Items.WOOL.black())
                        .attributes(ItemAttributeModifiers.builder()
                                .add(Attributes.ARMOR,
                                        new AttributeModifier(RipplesFabric.id("blade_hat_armor"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.HEAD)
                                .build())));

        var tommyGunKey = ResourceKey.create(Registries.ITEM, RipplesFabric.id("tommy_gun"));
        TOMMY_GUN = Registry.register(BuiltInRegistries.ITEM, tommyGunKey, new TommyGunItem(
                new Item.Properties()
                        .setId(tommyGunKey)
                        .stacksTo(1)
                        .attributes(TommyGunItem.createAttributes())));
    }

    public static void initialize() {
        DispenserBlock.registerBehavior(BLADE_HAT, BladeHatItem.DISPENSE_BEHAVIOR);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(BLADE_HAT);
            output.accept(TommyGunItem.createLoadedStack());
        });

        // Original weaponsmith_tommy_gun.json global loot modifier: 5% chance in village_weaponsmith chest with Ammo: 50.
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (BuiltInLootTables.VILLAGE_WEAPONSMITH.equals(key)) {
                builder.withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.05F))
                        .add(LootItem.lootTableItem(TOMMY_GUN)
                                .apply(SetCustomDataFunction.setCustomData(TommyGunItem.loadedCustomData()))));
            }
        });
    }

    /** Original BladeHatItem.canApplyAtEnchantingTable: allows Sharpness in addition to head armor enchantments. */
    public static boolean allowsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.is(BLADE_HAT) && enchantment.is(Enchantments.SHARPNESS);
    }

    private EquipmentContent() {}
}
