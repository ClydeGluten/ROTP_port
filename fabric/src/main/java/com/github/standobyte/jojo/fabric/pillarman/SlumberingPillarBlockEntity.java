package com.github.standobyte.jojo.fabric.pillarman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The original persistent absorption counter. It does not wake a boss or grant power. */
public final class SlumberingPillarBlockEntity extends BlockEntity {
    private int absorbedLife;
    public SlumberingPillarBlockEntity(BlockPos pos, BlockState state) { super(PillarWorldContent.SLUMBERING_BLOCK_ENTITY, pos, state); }
    public int getAbsorbedLife() { return absorbedLife; }
    public void incrementAbsorbed() {
        if (absorbedLife < Integer.MAX_VALUE) absorbedLife++;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); absorbedLife = Math.max(0, input.getIntOr("AbsorbedLife", 0)); }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.putInt("AbsorbedLife", absorbedLife); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
