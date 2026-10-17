package com.github.standobyte.jojo.fabric.equipment;

import com.github.standobyte.jojo.fabric.JojoSounds;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

/** Original BladeHatItem: sneak-use equips on head, normal use throws spinning BladeHatEntity, dispenser equips armor first else shoots. */
public final class BladeHatItem extends Item {
    public static final DispenseItemBehavior DISPENSE_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource blockSource, ItemStack stack) {
            return EquipmentDispenseItemBehavior.dispenseEquipment(blockSource, stack)
                    ? stack
                    : shootProjectile(blockSource, stack);
        }

        private ItemStack shootProjectile(BlockSource blockSource, ItemStack stack) {
            Level level = blockSource.level();
            Position position = DispenserBlock.getDispensePosition(blockSource);
            Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
            BladeHatEntity hat = new BladeHatEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1));
            hat.pickup = AbstractArrow.Pickup.ALLOWED;
            hat.shoot(direction.getStepX(), direction.getStepY() + 0.1F, direction.getStepZ(), 1.1F, 6.0F);
            level.addFreshEntity(hat);
            stack.shrink(1);
            return stack;
        }
    };

    public BladeHatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            // The source ArmorItem only equips an empty slot (modern swapWithEquipmentSlot swaps).
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                return InteractionResult.FAIL;
            }
            return super.use(level, player, hand);
        }
        if (level instanceof ServerLevel server) {
            BladeHatEntity hat = new BladeHatEntity(server, player, stack.copyWithCount(1));
            hat.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.75F, 0.5F);
            server.addFreshEntity(hat);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                JojoSounds.get("blade_hat_throw"), player.getSoundSource(),
                1.0F, 0.75F + player.getRandom().nextFloat() * 0.5F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
