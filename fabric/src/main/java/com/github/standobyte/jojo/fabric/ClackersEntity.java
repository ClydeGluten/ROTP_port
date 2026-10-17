package com.github.standobyte.jojo.fabric;
import com.github.standobyte.jojo.fabric.mixin.KnifeArrowAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
/** ClackersEntity and ItemProjectileEntity's recoverable, ricocheting throw. */
public final class ClackersEntity extends AbstractArrow {
    private float hamonDamage,hamonSpent;
    private boolean boomerangHit,leftOwner;
    public ClackersEntity(EntityType<? extends ClackersEntity> type,Level level){super(type,level);}
    public ClackersEntity(Level level,LivingEntity owner,ItemStack item){super(HamonContentEntities.CLACKERS,owner,level,item,null);if(owner instanceof Player p&&p.isCreative())pickup=Pickup.CREATIVE_ONLY;}
    public void setHamonDamage(float value){hamonDamage=value;}
    public void setHamonEnergySpent(float value){hamonSpent=value;}
    @Override protected ItemStack getDefaultPickupItem(){return new ItemStack(HamonItems.CLACKERS);}
    @Override public boolean isInGround(){return super.isInGround();}
    @Override public boolean isPickable(){return true;}
    @Override public void tick(){super.tick();if(boomerangHit&&inGroundTime>10)boomerangHit=false;
        if(getOwner()==null||!getBoundingBox().inflate(.3).intersects(getOwner().getBoundingBox()))leftOwner=true;}
    @Override protected boolean canHitEntity(Entity target){return target!=getOwner()&&(target.getType()!=HamonContentEntities.CLACKERS||super.canHitEntity(target));}
    @Override public Vec3 getMovementToShoot(double x,double y,double z,float speed,float spread){double noise=.0075F*spread;return new Vec3(x,y,z).normalize().add(random.nextGaussian()*noise,random.nextGaussian()*noise,random.nextGaussian()*noise).scale(speed);}
    @Override protected void onHitBlock(BlockHitResult hit){setSoundEvent(level().getBlockState(hit.getBlockPos()).getSoundType().getBreakSound());super.onHitBlock(hit);shakeTime=0;setNoGravity(false);setSoundEvent(getDefaultHitGroundSoundEvent());}
    @Override protected void onHitEntity(EntityHitResult hit){
        if(!(level() instanceof ServerLevel level))return;
        Entity target=hit.getEntity(),owner=getOwner();
        if(target instanceof ClackersEntity other){hamonDamage+=other.hamonDamage;other.hamonDamage=0;bounce();return;}
        if(owner instanceof LivingEntity living)living.setLastHurtMob(target);
        int previousFire=target.getRemainingFireTicks();boolean enderman=target.getType()==EntityTypes.ENDERMAN;
        if(isOnFire()&&!enderman)target.igniteForSeconds(5);
        float damage=(float)(getDeltaMovement().length()*((KnifeArrowAccess)(Object)this).jojo$getBaseDamage());
        if(isCritArrow())damage+=random.nextInt((int)(damage/2)+2);
        var source=damageSources().arrow(this,owner==null?this:owner);
        boolean physical=target instanceof LivingEntity living?StandDamage.hurt(level,living,source,damage):target.hurtServer(level,source,damage);
        boolean hamon=target instanceof LivingEntity living&&VampireHamon.damage(level,living,this,owner,hamonDamage);
        if(physical||hamon){if(owner instanceof LivingEntity living)HamonService.gainActionPoints(living,true,hamonSpent);boomerangHit=true;
            if(enderman)return;
            if(target instanceof LivingEntity living){net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffects(level,target,source);doPostHurtEffects(living);}
            playSound(getHitGroundSoundEvent(),1,1.2F/(random.nextFloat()*.2F+.9F));bounce();
        }else{target.setRemainingFireTicks(previousFire);setDeltaMovement(getDeltaMovement().scale(-.1));setYRot(getYRot()+180);yRotO+=180;if(getDeltaMovement().lengthSqr()<1E-7)bounce();}
        needsSync=true;
    }
    private void bounce(){Entity owner=getOwner();
        if(owner!=null){Vec3 end=position().add(getLookAngle().scale(distanceTo(owner)));
            var hit=ProjectileUtil.getEntityHitResult(level(),this,position(),end,getBoundingBox().expandTowards(end.subtract(position())).inflate(2.5),e->e!=owner&&canHitEntity(e),2.5F);
            setDeltaMovement(hit!=null?hit.getEntity().getEyePosition().subtract(position()).normalize().scale(getDeltaMovement().length()):getDeltaMovement().reverse());
            if(boomerangHit){setDeltaMovement(owner.getEyePosition().subtract(position()).normalize().scale(getDeltaMovement().length()/2));return;}}
        setDeltaMovement(getDeltaMovement().multiply(-.01,-.1,-.01));
    }
    @Override public void playerTouch(Player player){
        if(!level().isClientSide()&&(isInGround()||getOwner()==null||getOwner()==player)
                &&(pickup==Pickup.ALLOWED||pickup==Pickup.CREATIVE_ONLY&&player.isCreative())&&(isInGround()||isNoPhysics()||leftOwner)){
            if(pickup==Pickup.ALLOWED&&!player.getInventory().add(getPickupItem()))return;
            player.take(this,1);discard();if(boomerangHit)ClackersItem.voice(player,"joseph_clacker_boomerang");return;}
        super.playerTouch(player);
    }
    @Override protected void tickDespawn(){if(pickup!=Pickup.ALLOWED)super.tickDespawn();}
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putFloat("HamonDamage",hamonDamage);out.putFloat("HamonSpent",hamonSpent);out.putBoolean("BoomerangHit",boomerangHit);out.putInt("Age",tickCount);}
    @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);hamonDamage=in.getFloatOr("HamonDamage",0);hamonSpent=in.getFloatOr("HamonSpent",0);boomerangHit=in.getBooleanOr("BoomerangHit",false);tickCount=in.getIntOr("Age",0);}
}
