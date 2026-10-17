package com.github.standobyte.jojo.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.*;

/** Source Molotov impact fires and charged Sendo waves. */
public final class MolotovEntity extends ThrowableItemProjectile {
    public MolotovEntity(EntityType<? extends MolotovEntity> type, Level level) { super(type, level); }
    public MolotovEntity(Level level, LivingEntity owner, ItemStack stack) { super(HamonContentEntities.MOLOTOV, owner, level, stack); }
    public MolotovEntity(Level level, double x, double y, double z, ItemStack stack) { super(HamonContentEntities.MOLOTOV, x, y, z, level, stack); }
    @Override protected Item getDefaultItem() { return HamonItems.MOLOTOV; }
    @Override protected double getDefaultGravity() { return .05; }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1, random.nextFloat() * .1F + .9F);
            discard();
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level().isClientSide()) return;
        var charge = getAttached(HamonCharges.CHARGE);
        if (charge != null && charge.remaining() >= 0) {
            LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
            level().addFreshEntity(HamonSendoEntity.molotov(level(), owner, hit, 3, 2, charge.projectileDamage() / 4));
            level().addFreshEntity(HamonSendoEntity.molotov(level(), owner, hit, 2, 4, charge.projectileDamage() / 4));
        }
        setBlocksOnFire(hit.getBlockPos(), 3);
        setEntitiesOnFire(hit.getBlockPos(), 3);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level)) return;
        Entity target = hit.getEntity();
        target.hurtServer(level, new DamageSource(level.registryAccess().getOrThrow(DamageTypes.IN_FIRE), getOwner()), 2);
        target.igniteForSeconds(10);
        setBlocksOnFire(blockPosition(), 2);
        setEntitiesOnFire(blockPosition(), 2);
    }
    private void setBlocksOnFire(BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius,-radius,-radius), center.offset(radius,radius,radius)))
            if (pos.distManhattan(center) <= radius && level().isEmptyBlock(pos))
                level().setBlockAndUpdate(pos, BaseFireBlock.getState(level(), pos));
    }
    private void setEntitiesOnFire(BlockPos center, int radius) {
        for (Entity target : level().getEntities(this, new AABB(center).inflate(radius)))
            if (target.distanceToSqr(center.getX(), center.getY(), center.getZ()) < radius * radius) target.igniteForSeconds(4);
    }
}
