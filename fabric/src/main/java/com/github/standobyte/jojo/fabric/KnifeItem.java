package com.github.standobyte.jojo.fabric;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Original KnifeItem use/volley/tool rules; Forge-specific integrations live in adapters. */
public final class KnifeItem extends Item implements ProjectileItem {
    public static final int MAX_KNIVES_THROW = 8;
    public KnifeItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int count = player.isShiftKeyDown() ? 1 : Math.min(stack.getCount(), MAX_KNIVES_THROW);
        if (count <= 0) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            throwVolley(level, player, count, null);
            // The original primary throw sound really uses volume zero (KnifeItem:92).
            playThrowSound(level, player.getX(), player.getY(), player.getZ(), count, 0F);
            player.getCooldowns().addCooldown(stack, count * 3);
            if (!player.getAbilities().instabuild) stack.shrink(count);
            if (player instanceof ServerPlayer serverPlayer) {
                var stand = StandService.current(serverPlayer);
                if (stand != null && stand.isTheWorld()) KnifeService.onKnivesThrow(serverPlayer, stand, stack, count);
            }
        }
        return InteractionResult.SUCCESS;
    }

    static void throwVolley(Level level, Player player, int count, StarPlatinumEntity stand) {
        for (int i = 0; i < count; i++) {
            var knife = new KnifeEntity(level, player);
            if (stand != null) knife.setPos(stand.getX(), stand.getEyeY() - 0.1, stand.getZ());
            knife.setTimeStopFlightTicks(5);
            knife.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.5F, i == 0 ? 1F : 16F);
            level.addFreshEntity(knife);
        }
    }
    static void playThrowSound(Level level, double x, double y, double z, int count, float volume) {
        level.playSound(null, x, y, z, JojoSounds.get(count == 1 ? "knife_throw" : "knives_throw"),
                SoundSource.PLAYERS, volume, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        var knife = new KnifeEntity(level, pos.x(), pos.y(), pos.z());
        knife.pickup = AbstractArrow.Pickup.ALLOWED;
        return knife;
    }
    @Override public DispenseConfig createDispenseConfig() {
        return DispenseConfig.builder().positionFunction((source, direction) ->
                net.minecraft.world.level.block.DispenserBlock.getDispensePosition(source)).build();
    }
    @Override public void shoot(Projectile projectile, double x, double y, double z, float speed, float uncertainty) {
        // 1.16 adds lift to the launch direction; current defaults instead raise the spawn point.
        projectile.shoot(x, y + 0.1F, z, speed, uncertainty);
    }
    @Override public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (stack.getCount() > 1) return super.getDestroySpeed(stack, state);
        if (state.is(Blocks.COBWEB)) return 15F;
        // Material disappeared after 1.16: replace its plant/coral/vegetable tests with
        // equivalent block classes/tags, including newer members of those families.
        return state.getBlock() instanceof VegetationBlock || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.CORAL_BLOCKS) || state.is(BlockTags.CORALS)
                || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN) ? 1.5F : 1F;
    }
    @Override public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) { return state.is(Blocks.COBWEB); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        // Minecraft 26.3 moved axe stripping into data-driven block transformers. Select
        // its stripping entries only; original knives did not scrape/wax copper.
        var transformer = level.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER).getOrThrow(BlockTransformers.AXE).value();
        for (var transform : transformer.transforms()) {
            if (!transform.sound().value().location().equals(SoundEvents.AXE_STRIP.value().location())) continue;
            var stripped = transform.blockStateProvider().value().getOptionalState(level, level.getRandom(), pos);
            if (stripped == null) continue;
            level.playSound(context.getPlayer(), pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1F, 1F);
            if (!level.isClientSide()) level.setBlock(pos, stripped, 11);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
