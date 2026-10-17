package com.github.standobyte.jojo.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.function.Consumer;

/** Original fire-source search, fuel consumption and throwable-infusion carrier. */
public final class MolotovItem extends Item implements ProjectileItem {
    public MolotovItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Predict the same eligibility on both sides; only the server consumes fuel and spawns the bottle.
        if (level.isClientSide()) return canUseFire(player,level) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        if (!useFire(player, (ServerLevel) level)) {
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("jojo.message.action_condition.molotov_fire"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        var bottle = new MolotovEntity(level, player, stack);
        bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, .75F, 1);
        level.addFreshEntity(bottle);
        if (!player.isCreative()) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.isSilent()) level.playSound(null, player.getX(), player.getY(), player.getZ(),
                JojoSounds.get("molotov_throw"), player.getSoundSource(), .5F, .4F / (player.getRandom().nextFloat() * .4F + .8F));
        return InteractionResult.SUCCESS;
    }
    private static boolean nearbyFire(Player player,Level level) {
        if (player.isOnFire()) return true;
        BlockPos center = player.blockPosition().above();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2,-2,-2), center.offset(2,2,2))) {
            var state = level.getBlockState(pos);
            var block = state.getBlock();
            if (block instanceof BaseFireBlock || ((block instanceof AbstractFurnaceBlock || block instanceof CampfireBlock
                    || block instanceof TorchBlock && block != Blocks.REDSTONE_TORCH)
                    && (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)))
                    || level.getFluidState(pos).is(FluidTags.LAVA)) return true;
        }
        return false;
    }
    private static boolean canUseFire(Player player,Level level) {
        return nearbyFire(player,level) || !HamonItems.find(player,stack->stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem).isEmpty();
    }
    public static boolean useFire(Player player, ServerLevel level) {
        if(nearbyFire(player,level))return true;
        ItemStack flint = HamonItems.find(player, stack -> stack.getItem() instanceof FlintAndSteelItem);
        if (!flint.isEmpty()) {
            if (!player.isSilent()) level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE, player.getSoundSource(), 1, player.getRandom().nextFloat() * .4F + .8F);
            flint.hurtAndBreak(1, level, player instanceof ServerPlayer p ? p : null, broken -> {
                if (!player.isSilent()) level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_BREAK, player.getSoundSource(), .8F, .8F + player.getRandom().nextFloat() * .4F);
            });
            return true;
        }
        ItemStack charge = HamonItems.find(player, stack -> stack.getItem() instanceof FireChargeItem);
        if (charge.isEmpty()) return false;
        if (!player.isSilent()) level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, player.getSoundSource(), 1, (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * .2F + 1);
        charge.shrink(1); // The source consumes fire charges even in creative mode.
        return true;
    }
    @Override public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new MolotovEntity(level, pos.x(), pos.y(), pos.z(), stack);
    }
    @Override public DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder().positionFunction((source, direction) -> DispenserBlock.getDispensePosition(source))
                .power(1.375F).uncertainty(3).build();
    }
    @Override public void shoot(Projectile projectile, double x, double y, double z, float speed, float uncertainty) {
        projectile.shoot(x, y + .1F, z, speed, uncertainty);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(" "));
        tooltip.accept(Component.translatable("item.jojo.molotov.reference_quote").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.jojo.dono_tooltip", "ArchLunatic").withStyle(ChatFormatting.DARK_GRAY));
    }
}
