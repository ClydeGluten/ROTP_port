package com.github.standobyte.jojo.fabric.equipment;

import com.github.standobyte.jojo.fabric.ClackersItem;
import com.github.standobyte.jojo.fabric.HamonService;
import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.VampireHamon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Port of TommyGunItem: 50 max ammo in CUSTOM_DATA ("Ammo"), reload on sneak-use from iron nuggets + gunpowder
 * (8 bullets per gunpowder), 2-tick firing cadence over 100-tick use duration, progressive recoil, gunshot muzzle
 * flash timer ("GunshotTick"), and Joseph Hamon voice lines / melee Hamon strike.
 */
public final class TommyGunItem extends Item {
    public static final int MAX_AMMO = 50;
    public static final int BULLETS_PER_GUNPOWDER = 8;
    public static final int MAX_USE_DURATION = 100;

    public TommyGunItem(Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(RipplesFabric.id("tommy_gun_entity_reach"), 0.5D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.BLOCK_INTERACTION_RANGE,
                        new AttributeModifier(RipplesFabric.id("tommy_gun_block_reach"), 0.5D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static CompoundTag loadedCustomData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Ammo", MAX_AMMO);
        return tag;
    }

    public static ItemStack createLoadedStack() {
        ItemStack stack = new ItemStack(EquipmentContent.TOMMY_GUN);
        setAmmo(stack, MAX_AMMO);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAmmo(stack) < MAX_AMMO;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getAmmo(stack) / (float) MAX_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, (float) getAmmo(stack) / (float) MAX_AMMO) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        int ammo = getAmmo(stack);
        int useDuration = getUseDuration(stack, entity);
        int t = useDuration - remainingTicks;
        boolean shotTick = t % 2 == 0;
        if (remainingTicks <= 1) {
            entity.releaseUsingItem();
            return;
        }
        if (!level.isClientSide()) {
            if (remainingTicks == useDuration - 14 && ammo == MAX_AMMO - 7 && josephVoiceLine(entity)) {
                ClackersItem.voice(entity, "joseph_scream_shooting");
            }
            if (ammo > 0) {
                if (shotTick) {
                    TommyGunBulletEntity bullet = new TommyGunBulletEntity(entity, level);
                    Vec3 pos = entity.getEyePosition(1.0F).subtract(0.0D, bullet.getBbHeight() / 2.0D, 0.0D).add(entity.getLookAngle());
                    bullet.shootFromRotation(entity, 2.0F, 0.0F);
                    bullet.setPos(pos.x, pos.y, pos.z);
                    level.addFreshEntity(bullet);
                    if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
                        consumeAmmo(stack, 1);
                    }
                    setGunshotTick(stack, (byte) 3);
                }
            } else {
                entity.releaseUsingItem();
            }
        }
        if (ammo > 0) {
            if (shotTick) {
                RandomSource random = entity.getRandom();
                if (entity.getType() == EntityTypes.PLAYER ? level.isClientSide() : !level.isClientSide()) {
                    float recoil = 1.0F + Math.min((1.0F - (float) remainingTicks / (float) useDuration) * 6.0F, 3.0F);
                    entity.setYRot(entity.getYRot() + (random.nextFloat() - 0.5F) * 0.3F * recoil);
                    entity.setXRot(Mth.clamp(entity.getXRot() - random.nextFloat() * 0.75F * recoil, -90.0F, 90.0F));
                }
            }
        } else {
            entity.playSound(JojoSounds.get("tommy_gun_no_ammo"), 1.0F, 1.0F);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        byte tick = getGunshotTick(stack);
        if (tick > 0) {
            setGunshotTick(stack, (byte) (tick - 1));
        }
    }

    public static byte getGunshotTick(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getByteOr("GunshotTick", (byte) 0);
    }

    public static void setGunshotTick(ItemStack stack, byte tick) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (tick <= 0) {
                tag.remove("GunshotTick");
            } else {
                tag.putByte("GunshotTick", tick);
            }
        });
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        if (!level.isClientSide() && remainingTicks <= 1 && josephVoiceLine(entity)) {
            ClackersItem.voice(entity, "joseph_war_declaration");
        }
        return false;
    }

    public static boolean josephVoiceLine(LivingEntity entity) {
        return HamonService.hasHamon(entity) && "joseph".equals(HamonService.state(entity).technique());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return reload(stack, player, level) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        } else {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    public static int getAmmo(ItemStack gun) {
        CustomData data = gun.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Mth.clamp(data.copyTag().getIntOr("Ammo", 0), 0, MAX_AMMO);
    }

    public static void setAmmo(ItemStack gun, int ammo) {
        int clamped = Mth.clamp(ammo, 0, MAX_AMMO);
        CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putInt("Ammo", clamped));
    }

    private static boolean consumeAmmo(ItemStack gun, int amount) {
        int ammo = getAmmo(gun);
        if (ammo < amount) {
            return false;
        }
        if (amount > 0) {
            setAmmo(gun, ammo - amount);
        }
        return true;
    }

    public static boolean reload(ItemStack gunStack, Entity entity, Level level) {
        int ammoToLoad = MAX_AMMO - getAmmo(gunStack);
        if (ammoToLoad > 0) {
            if (entity instanceof Player player) {
                if (!player.getAbilities().instabuild) {
                    ammoToLoad = consumeReloadMaterials(player, ammoToLoad);
                    if (!level.isClientSide()) {
                        player.getCooldowns().addCooldown(gunStack, ammoToLoad * 2);
                    }
                }
            }
            if (ammoToLoad > 0) {
                if (!level.isClientSide()) {
                    setAmmo(gunStack, getAmmo(gunStack) + ammoToLoad);
                }
                return true;
            }
        }
        return false;
    }

    private static int consumeReloadMaterials(Player player, int ammoToLoad) {
        int ironNuggets = 0;
        int gunpowder = 0;
        var inventory = player.getInventory();
        int size = inventory.getContainerSize();

        for (int i = 0; i < size; ++i) {
            ItemStack invStack = inventory.getItem(i);
            if (!invStack.isEmpty()) {
                if (invStack.is(Items.IRON_NUGGET)) {
                    ironNuggets += invStack.getCount();
                } else if (invStack.is(Items.GUNPOWDER)) {
                    gunpowder += invStack.getCount();
                }
            }
        }

        ammoToLoad = Math.min(Math.min(ironNuggets, gunpowder * BULLETS_PER_GUNPOWDER), ammoToLoad);
        ironNuggets = ammoToLoad;
        gunpowder = Mth.ceil((double) ammoToLoad / (double) BULLETS_PER_GUNPOWDER);

        if (!player.level().isClientSide()) {
            for (int i = 0; i < size; ++i) {
                ItemStack invStack = inventory.getItem(i);
                if (!invStack.isEmpty()) {
                    if (invStack.is(Items.IRON_NUGGET) && ironNuggets > 0) {
                        int shrinkBy = Math.min(invStack.getCount(), ironNuggets);
                        invStack.shrink(shrinkBy);
                        ironNuggets -= shrinkBy;
                    } else if (invStack.is(Items.GUNPOWDER) && gunpowder > 0) {
                        int shrinkBy = Math.min(invStack.getCount(), gunpowder);
                        invStack.shrink(shrinkBy);
                        gunpowder -= shrinkBy;
                    }
                    if (ironNuggets == 0 && gunpowder == 0) {
                        break;
                    }
                }
            }
        }
        return ammoToLoad;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity user) {
        if (user.level() instanceof ServerLevel server && josephVoiceLine(user)
                && HamonService.consumeResourceEnergy(user, 200.0F) > 0.0F) {
            if (VampireHamon.damage(server, target, user, 0.15F)) {
                target.damageCooldownTime = 0;
                HamonService.gainActionPoints(user, true, 200.0F);
                ClackersItem.voice(user, "joseph_shoot");
            }
        }
        super.hurtEnemy(stack, target, user);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.jojo.tommy_gun.reload_prompt",
                Component.keybind("key.sneak"), Component.keybind("key.use")).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
