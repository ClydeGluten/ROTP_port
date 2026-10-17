package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Keeps the original placed mask's complete item components and activation timer. */
public final class StoneMaskBlockEntity extends BlockEntity {
    private ItemStack mask = ItemStack.EMPTY;
    private int activationTicks;
    public StoneMaskBlockEntity(BlockPos pos, BlockState state) { super(VampireBlocks.MASK_BLOCK_ENTITY, pos, state); }
    public ItemStack stack() { return mask.isEmpty() ? new ItemStack(getBlockState().getBlock().asItem()) : mask.copy(); }
    public void setStack(ItemStack stack) { mask = stack.copyWithCount(1); setChanged(); }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); mask = input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY); activationTicks = input.getIntOr("ActivationTicks", 0); }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.store("Item", ItemStack.CODEC, mask); output.putInt("ActivationTicks", activationTicks); }
    public void activate() { activationTicks = 100; setChanged(); level.setBlockAndUpdate(worldPosition, getBlockState().setValue(StoneMaskBlock.BLOOD_ACTIVATION, true)); }
    public void tick() {
        if (activationTicks <= 0) return;
        if (--activationTicks == 0) {
            level.playSound(null, worldPosition, JojoSounds.get("stone_mask_deactivation"), SoundSource.BLOCKS, 1, 1);
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(StoneMaskBlock.BLOOD_ACTIVATION, false));
        }
        setChanged();
    }
}
