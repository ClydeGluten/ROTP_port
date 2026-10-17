package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.JojoSounds;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;

/** Original boiling blood retains LavaFluid flow, ignition, contact and water reactions. */
public abstract class BoilingBloodFluid extends LavaFluid {
    @Override public Fluid getFlowing() { return PillarWorldContent.FLOWING_BOILING_BLOOD; }
    @Override public Fluid getSource() { return PillarWorldContent.BOILING_BLOOD; }
    @Override public Item getBucket() { return PillarWorldContent.BOILING_BLOOD_BUCKET; }
    @Override public boolean isSame(Fluid fluid) { return fluid == getSource() || fluid == getFlowing(); }
    @Override public BlockState createLegacyBlock(FluidState state) {
        return PillarWorldContent.BOILING_BLOOD_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }
    @Override public ParticleOptions getDripParticle() { return ParticleTypes.DRIPPING_LAVA; }
    @Override public Optional<SoundEvent> getPickupSound() { return Optional.of(JojoSounds.get("bucket_fill_boiling_blood")); }
    @Override public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir() && !level.getBlockState(above).isSolidRender()) {
            if (random.nextInt(100) == 0) {
                double x = pos.getX() + random.nextDouble(), y = pos.getY() + 1, z = pos.getZ() + random.nextDouble();
                level.addParticle(PillarWorldContent.BOILING_BLOOD_POP, x, y, z, 0, 0, 0);
                level.playLocalSound(x, y, z, JojoSounds.get("boiling_blood_pop"), SoundSource.BLOCKS,
                        .2F + random.nextFloat() * .2F, .9F + random.nextFloat() * .15F, false);
            }
            if (random.nextInt(200) == 0) level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                    JojoSounds.get("boiling_blood_ambient"), SoundSource.BLOCKS,
                    .2F + random.nextFloat() * .2F, .9F + random.nextFloat() * .15F, false);
        }
    }
    public static final class Flowing extends BoilingBloodFluid {
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) { super.createFluidStateDefinition(builder); builder.add(LEVEL); }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
    public static final class Source extends BoilingBloodFluid {
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }
}
