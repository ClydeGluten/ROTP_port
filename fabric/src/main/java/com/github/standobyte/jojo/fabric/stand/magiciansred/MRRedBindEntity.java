package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Owner-bound fire ribbon, including source instance-owned immobilization and kick conversion. */
public final class MRRedBindEntity extends MRProjectileEntity {
    private static final EntityDataAccessor<Integer> TARGET=SynchedEntityData.defineId(MRRedBindEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DISTANCE=SynchedEntityData.defineId(MRRedBindEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> KICK=SynchedEntityData.defineId(MRRedBindEntity.class,EntityDataSerializers.BOOLEAN),RETRACT=SynchedEntityData.defineId(MRRedBindEntity.class,EntityDataSerializers.BOOLEAN);
    private LivingEntity attached;
    private MobEffectInstance ownedEffect;
    private double distance;
    private int closeTicks;
    private final Set<Entity> dragged=new HashSet<>();
    public MRRedBindEntity(EntityType<? extends MRRedBindEntity> type,Level level){super(type,level);}
    public MRRedBindEntity(MagiciansRedEntity owner){this(MagiciansRedActions.BIND_ENTITY,owner.level());initializeOwner(owner);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(TARGET,-1);b.define(KICK,false);b.define(RETRACT,false);b.define(DISTANCE,0L);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(DISTANCE))distance=Double.longBitsToDouble(entityData.get(DISTANCE));}
    @Override protected Vec3 relativeOffset(){return new Vec3(0,-.25,.5);}
    @Override public int lifespan(){return entityData.get(TARGET)>=0?(kick()?Integer.MAX_VALUE:100):7;}
    @Override protected float baseDamage(){return 0;}
    @Override protected float hardness(){return 0;}
    @Override public boolean isOnFire(){return false;}
    public boolean kick(){return entityData.get(KICK);}
    @Override public boolean isInvisibleTo(Player player){return getOwner()!=null&&getOwner().isInvisibleTo(player)||super.isInvisibleTo(player);}
    public LivingEntity attached(){if(attached==null&&level().getEntity(entityData.get(TARGET)) instanceof LivingEntity living)attached=living;return attached;}
    public Vec3 origin(float partial){var owner=getOwner();return owner==null?position():owner.getEyePosition(partial).add(relativeOffset().yRot(-owner.getYRot()*((float)Math.PI/180)));}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;
        if(isInWater()||level().isRainingAt(blockPosition())){clearFire();return;}
        if(getOwner()==null){if(!level().isClientSide())discard();return;}
        dragged.forEach(e->e.setDeltaMovement(Vec3.ZERO));dragged.clear();super.tick();if(!isAlive())return;
        if(!level().isClientSide()&&(!(getOwner() instanceof MagiciansRedEntity stand)||stand.task()!=MagiciansRedEntity.Task.RED_BIND&&!kick())){discard();return;}
        var bound=attached();if(bound==null)return;var owner=getOwner();
        if(!bound.isAlive()||owner.distanceToSqr(bound)>100){if(!level().isClientSide())discard();return;}
        if(level() instanceof ServerLevel server){if(bound.getRemainingFireTicks()%20==0||bound.getRemainingFireTicks()<=0)ignite(bound,3);suffocate(server,bound,kick()?1:.0025F);}
        var toward=owner.position().subtract(bound.position());
        if(toward.lengthSqr()>4){drag(bound,toward.normalize().scale(.2));closeTicks=0;}
        else if(!level().isClientSide()&&!kick()&&closeTicks++>10)discard();
    }
    @Override public void clearFire(){super.clearFire();extinguish();}
    @Override protected List<HitResult> trace(){
        Vec3 from=origin(1),to=position().add(getDeltaMovement());List<HitResult> hits=new ArrayList<>();
        for(var entity:level().getEntities(this,new AABB(from,to).inflate(1),this::canHitEntity)){
            var box=entity.getBoundingBox().inflate(entity.getPickRadius()+getBbWidth()/2);var point=box.contains(from)?Optional.of(from):box.clip(from,to);
            point.ifPresent(pos->hits.add(new EntityHitResult(entity,pos)));
        }
        hits.sort(Comparator.comparingDouble(hit->hit.getLocation().distanceToSqr(from)));
        var block=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(block.getType()!=HitResult.Type.MISS)hits.add(block);
        return hits;
    }
    @Override protected boolean hurtTarget(ServerLevel server,Entity target){
        if(attached()!=null||!(target instanceof LivingEntity living)||living.isInvulnerableTo(server,damageSource(server))||MagiciansRedActions.isBlocking(living))return false;
        attached=living;entityData.set(TARGET,living.getId());boolean owns=ownedEffect==living.getEffect(HamonEffects.IMMOBILIZE);
        living.addEffect(new MobEffectInstance(HamonEffects.IMMOBILIZE,Math.max(1,lifespan()-tickCount)));
        if(owns)ownedEffect=living.getEffect(HamonEffects.IMMOBILIZE);return true;
    }
    @Override protected void afterBlockHit(ServerLevel server,BlockHitResult hit,boolean destroyed){if(!destroyed)entityData.set(RETRACT,true);}
    @Override protected void breakProjectile(HitResult hit){}
    @Override protected void moveProjectile(){
        var bound=attached();if(bound!=null){setPos(bound.getX(),bound.getY(kick()?.75:.5),bound.getZ());setRot(bound.getYRot(),bound.getXRot());return;}
        var owner=getOwner();if(owner==null)return;distance+=(entityData.get(RETRACT)?-1:1)*projectileSpeedFactor();
        if(!level().isClientSide())entityData.set(DISTANCE,Double.doubleToLongBits(distance));
        if(entityData.get(RETRACT)&&distance<=0){if(!level().isClientSide())discard();return;}
        var next=origin(1).add(owner.getLookAngle().scale(distance));
        if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(next))){if(level().isClientSide())discard();else super.moveProjectile();return;}
        setDeltaMovement(next.subtract(position()));setPos(next);setRot(owner.getYRot(),owner.getXRot());
    }
    private void drag(Entity target,Vec3 movement){
        var vehicle=target.getRootVehicle();dragOne(vehicle,movement);if(vehicle instanceof StandManifestation stand&&stand.owner()!=null)dragOne(vehicle,movement);
    }
    private void dragOne(Entity entity,Vec3 movement){
        if(entity instanceof LivingEntity living&&(living.hasEffect(HamonEffects.IMMOBILIZE)||living.hasEffect(VampireCombatService.STUN))){entity.move(MoverType.PLAYER,movement);return;}
        entity.setDeltaMovement(movement);dragged.add(entity);
    }
    public void setKickAttack(){
        entityData.set(KICK,true);var target=attached();if(target==null)return;removeOwnedEffect(target);
        target.addEffect(new MobEffectInstance(VampireCombatService.STUN,Integer.MAX_VALUE-tickCount));ownedEffect=target.getEffect(VampireCombatService.STUN);
    }
    private void removeOwnedEffect(LivingEntity target){if(ownedEffect!=null&&target.getEffect(ownedEffect.getEffect())==ownedEffect)target.removeEffect(ownedEffect.getEffect());ownedEffect=null;}
    @Override public void remove(RemovalReason reason){
        if(!level().isClientSide()){var target=attached();if(target!=null)removeOwnedEffect(target);dragged.forEach(e->e.setDeltaMovement(Vec3.ZERO));dragged.clear();}
        super.remove(reason);
    }
    private static void suffocate(ServerLevel server,LivingEntity target,float speed){
        if(target.canBreatheUnderwater()||target instanceof Player&&SpeciesHooks.undeadOrVampiric(target)||target instanceof IronGolem
                ||net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(RipplesFabric.id("dying_body")).map(target::hasEffect).orElse(false))return;
        if(target.getAirSupply()>0){
            if(HamonService.hasHamon(target)){speed/=1+HamonService.state(target).breathingLevel()*.04F;HamonService.suffocateTick(target,speed);}
            double reduction=target.getMaxAirSupply()*Math.clamp(speed,0,1);int amount=(int)reduction+(target.getRandom().nextDouble()<reduction-(int)reduction?1:0)+4;
            target.setAirSupply(Math.max(target.getAirSupply()-amount,-18));
        }else target.hurtServer(server,target.damageSources().drown(),1);
    }
}
