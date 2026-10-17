package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Feature-local port of active DamagingEntity/ModdedProjectileEntity behavior used by MR's fiery projectiles. */
public abstract class MRProjectileEntity extends Projectile implements SilverChariotEntity.DeflectableProjectile {
    private static final EntityDataAccessor<Integer> OWNER_ID=SynchedEntityData.defineId(MRProjectileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEFLECTED=SynchedEntityData.defineId(MRProjectileEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> SPEED=SynchedEntityData.defineId(MRProjectileEntity.class,EntityDataSerializers.LONG);
    protected float damageFactor=1;
    protected double speedFactor=1;
    private LivingEntity cachedOwner;
    protected MRProjectileEntity(EntityType<? extends MRProjectileEntity> type,Level level){super(type,level);setNoGravity(true);}
    protected void initializeOwner(MagiciansRedEntity owner){initializeOwner(owner,true);}
    protected void initializeOwner(MagiciansRedEntity owner,boolean scaledByStand){
        setOwner(owner);damageFactor=scaledByStand?(float)(owner.attackDamage()/8):1;speedFactor=scaledByStand?owner.attackSpeed()/8:1;entityData.set(SPEED,Double.doubleToLongBits(speedFactor));
        LivingEntity origin=owner;
        setPos(origin.getEyePosition().add(relativeOffset().add(pitchOffset().xRot(-owner.getXRot()*(float)Math.PI/180)).yRot(-owner.getYRot()*(float)Math.PI/180)));
        setRot(owner.getYRot(),owner.getXRot());
    }
    protected Vec3 relativeOffset(){return new Vec3(0,-.3,0);}
    protected Vec3 pitchOffset(){return Vec3.ZERO;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER_ID,-1);b.define(DEFLECTED,false);b.define(SPEED,Double.doubleToLongBits(1));}
    protected double projectileSpeedFactor(){return Double.longBitsToDouble(entityData.get(SPEED));}
    @Override public void setOwner(Entity entity){super.setOwner(entity);cachedOwner=entity instanceof LivingEntity living?living:null;entityData.set(OWNER_ID,entity==null?-1:entity.getId());}
    @Override public LivingEntity getOwner(){
        if(cachedOwner==null){var entity=super.getOwner();if(entity==null)entity=level().getEntity(entityData.get(OWNER_ID));if(entity instanceof LivingEntity living)cachedOwner=living;}
        return cachedOwner;
    }
    public LivingEntity user(){var owner=getOwner();return owner instanceof StandManifestation stand?stand.owner():owner;}
    public abstract int lifespan();
    protected abstract float baseDamage();
    protected abstract float hardness();
    protected float knockbackMultiplier(){return 1;}
    protected boolean fireDamage(){return false;}
    protected int igniteSeconds(){return 0;}
    protected ClipContext.Block blockMode(){return ClipContext.Block.COLLIDER;}
    public float damageAmount(){return baseDamage()*damageFactor*StandConfig.server().standPowerMultiplier();}
    @Override public boolean isFiery(){return true;}
    @Override public boolean canBeDeflected(Entity context){return true;}
    @Override public boolean standDamage(){return true;}
    @Override public boolean isInvisible(){return true;}
    @Override public boolean isInvisibleTo(Player player){return !StandPerception.canSeeStand(player)||!StandPerception.trueSpectator(player)&&super.isInvisible();}
    @Override public boolean canBeEvaded(LivingEntity context){return true;}
    @Override public void setDeflected(){if(!level().isClientSide())entityData.set(DEFLECTED,true);}
    public boolean isDeflected(){return entityData.get(DEFLECTED);}
    @Override public boolean isPickable(){return true;}
    @Override public float getPickRadius(){return 1;}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;
        var owner=getOwner();if(!level().isClientSide()&&(tickCount>lifespan()||owner!=null&&!owner.isAlive())){discard();return;}
        super.tick();
        if(level() instanceof ServerLevel server&&!isRemoved())for(var hit:trace()){
            if(isRemoved())break;
            if(hit instanceof EntityHitResult entityHit){boolean hurt=hurtTarget(server,entityHit.getEntity());afterEntityHit(entityHit,hurt);breakProjectile(hit);}
            else if(hit instanceof BlockHitResult blockHit&&hit.getType()==HitResult.Type.BLOCK){
                boolean destroyed=permitted(server,blockHit.getBlockPos(),getOwner())&&destroyHitBlock(server,blockHit);
                afterBlockHit(server,blockHit,destroyed);breakProjectile(hit);
            }
        }
        moveProjectile();
    }
    protected List<HitResult> trace(){
        Vec3 from=position(),to=from.add(getDeltaMovement());HitResult result=level().clip(new ClipContext(from,to,blockMode(),ClipContext.Fluid.NONE,this));
        double closest=result.getType()==HitResult.Type.MISS?from.distanceToSqr(to):from.distanceToSqr(result.getLocation());
        for(var entity:level().getEntities(this,getBoundingBox().expandTowards(getDeltaMovement()).inflate(1),this::canHitEntity)){
            var point=entity.getBoundingBox().inflate(entity.getPickRadius()).clip(from,to);
            if(point.isPresent()&&from.distanceToSqr(point.get())<=closest){closest=from.distanceToSqr(point.get());result=new EntityHitResult(entity,point.get());}
        }
        return List.of(result);
    }
    @Override protected boolean canHitEntity(Entity entity){
        if(!entity.isAlive()||entity.isSpectator()||!entity.isPickable()||entity==this)return false;
        var owner=getOwner();if(owner==null)return super.canHitEntity(entity);
        if(entity==owner||entity==user())return isDeflected()&&super.canHitEntity(entity);
        if(entity instanceof Projectile projectile&&projectile.getOwner()!=null&&projectile.getOwner().getUUID().equals(owner.getUUID()))return false;
        if(entity instanceof LivingEntity living&&!owner.canAttack(living))return false;
        if(user() instanceof Player player&&entity instanceof Player victim&&!player.canHarmPlayer(victim))return false;
        return super.canHitEntity(entity);
    }
    protected DamageSource damageSource(ServerLevel server){var source=new DamageSource(server.registryAccess().getOrThrow(fireDamage()?DamageTypes.FIREBALL:DamageTypes.ARROW),this,getOwner());return isDeflected()?StandDamage.marked(source,0).allowSelf():source;}
    protected boolean hurtTarget(ServerLevel server,Entity target){
        int oldFire=target.getRemainingFireTicks();Vec3 motion=target.getDeltaMovement();if(igniteSeconds()>0)ignite(target,igniteSeconds());
        var source=damageSource(server);float damage=damageAmount();boolean hurt=target instanceof LivingEntity living?StandDamage.hurt(server,living,source,damage):target.hurtServer(server,source,damage);
        if(!hurt)target.setRemainingFireTicks(oldFire);else{
            if(isOnFire())target.igniteForSeconds(5);
            var user=user();if(target instanceof LivingEntity living&&user!=null){living.setLastHurtByMob(user);if(user instanceof Player player)living.setLastHurtByPlayer(player,100);}
        }
        if(knockbackMultiplier()<1)target.setDeltaMovement(motion.add(target.getDeltaMovement().subtract(motion).scale(Math.max(0,knockbackMultiplier()))));
        return hurt;
    }
    protected void afterEntityHit(EntityHitResult hit,boolean hurt){}
    protected void afterBlockHit(ServerLevel server,BlockHitResult hit,boolean destroyed){}
    protected void breakProjectile(HitResult hit){if(!level().isClientSide())discard();}
    protected void moveProjectile(){setPos(position().add(getDeltaMovement()));}
    @Override public void shoot(double x,double y,double z,float speed,float spread){super.shoot(x,y,z,speed,spread);var v=getDeltaMovement();setRot((float)(Math.atan2(-v.x,v.z)*180/Math.PI),(float)(-Math.atan2(v.y,v.horizontalDistance())*180/Math.PI));yRotO=getYRot();xRotO=getXRot();}
    /** ModdedProjectileEntity does not inherit shooter motion. */
    public void shootFromStand(float pitch,float yaw,float speed,float spread){var v=Vec3.directionFromRotation(pitch,yaw);shoot(v.x,v.y,v.z,speed,spread);}
    protected boolean canBreak(BlockState state,BlockPos pos){float value=state.getDestroySpeed(level(),pos);return value>=0&&value<=hardness();}
    protected boolean destroyHitBlock(ServerLevel server,BlockHitResult hit){
        if(!server.getGameRules().get(JojoGameRules.BREAK_BLOCKS))return false;
        var pos=hit.getBlockPos();var state=server.getBlockState(pos);var fire=FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        if(isFiery()&&fire!=null&&fire.getBurnOdds()>0){catchFire(server,pos,state,getOwner());return false;}
        if(!canBreak(state,pos))return false;
        boolean drops=!(user() instanceof Player player)||!player.isCreative();
        try(var capture=RestorationHooks.begin(server,pos,state,server.getBlockEntity(pos))){boolean destroyed=server.destroyBlock(pos,drops,getOwner());capture.commit(destroyed);return destroyed;}
    }
    public static boolean permitted(ServerLevel server,BlockPos pos,Entity owner){
        var user=owner instanceof StandManifestation stand?stand.owner():owner;
        return server.hasChunkAt(pos)&&(!(user instanceof Player player)||player.mayBuild()&&server.mayInteract(player,pos)
                &&PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server,player,pos,server.getBlockState(pos),server.getBlockEntity(pos)));
    }
    public static void catchFire(ServerLevel server,BlockPos pos,BlockState state,LivingEntity owner){
        if(state.getBlock() instanceof TntBlock){RestorationHooks.remember(server,pos,state,server.getBlockEntity(pos),List.of());TntBlock.prime(server,pos,owner,ItemStack.EMPTY);server.removeBlock(pos,false);}
    }
    public static void ignite(Entity target,int seconds){
        target.igniteForSeconds(seconds);
    }
    protected void extinguish(){
        if(level() instanceof ServerLevel server&&!isRemoved()){
            server.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,getX(),getY(),getZ(),8,getBbWidth()/2,getBbHeight()/2,getBbWidth()/2,0);discard();
        }
    }
    @Override public boolean hurtServer(ServerLevel server,DamageSource source,float damage){
        if(isInvulnerableToBase(source)||!StandDamage.isStand(source))return false;
        discard();return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putFloat("DamageFactor",damageFactor);out.putDouble("SpeedFactor",speedFactor);out.putInt("Age",tickCount);out.putBoolean("IsDeflected",isDeflected());}
    @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);damageFactor=in.getFloatOr("DamageFactor",1);speedFactor=in.getDoubleOr("SpeedFactor",1);entityData.set(SPEED,Double.doubleToLongBits(speedFactor));tickCount=in.getIntOr("Age",0);entityData.set(DEFLECTED,in.getBooleanOr("IsDeflected",false));cachedOwner=null;}
}
