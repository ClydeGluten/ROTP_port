package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Six-hundred-tick user-owned detector; nearest selection keeps the original integer comparator. */
public final class MRDetectorEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(MRDetectorEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FOUND=SynchedEntityData.defineId(MRDetectorEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DX=SynchedEntityData.defineId(MRDetectorEntity.class,EntityDataSerializers.FLOAT),DY=SynchedEntityData.defineId(MRDetectorEntity.class,EntityDataSerializers.FLOAT),DZ=SynchedEntityData.defineId(MRDetectorEntity.class,EntityDataSerializers.FLOAT);
    private LivingEntity cachedOwner;
    public MRDetectorEntity(EntityType<? extends MRDetectorEntity> type,Level level){super(type,level);setNoGravity(true);}
    public MRDetectorEntity(LivingEntity owner){this(MagiciansRedActions.DETECTOR_ENTITY,owner.level());cachedOwner=owner;entityData.set(OWNER,owner.getId());setPos(owner.position());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,-1);b.define(FOUND,false);b.define(DX,0F);b.define(DY,0F);b.define(DZ,0F);}
    public LivingEntity owner(){if(cachedOwner==null&&level().getEntity(entityData.get(OWNER)) instanceof LivingEntity living)cachedOwner=living;return cachedOwner;}
    public boolean detected(){return entityData.get(FOUND);}
    public Vec3 detectedDirection(){return new Vec3(entityData.get(DX),entityData.get(DY),entityData.get(DZ));}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;
        if(isInWater()||level().isRainingAt(blockPosition())){clearFire();return;}super.tick();var owner=owner();
        if(tickCount>=600||owner==null||!owner.isAlive()){if(!level().isClientSide())discard();return;}
        setPos(owner.getEyePosition().add(new Vec3(-.5,-.5,1.5).yRot(-owner.getYRot()*((float)Math.PI/180))));
        if(level().isClientSide())return;
        var closest=level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(15),entity->entity.isAlive()&&!entity.isSpectator()&&!(entity instanceof ArmorStand)&&entity!=owner&&!entity.isAlliedTo(owner)&&!(entity instanceof StandManifestation stand&&stand.owner()==owner))
                .stream().min((a,b)->(int)(a.distanceToSqr(this)-b.distanceToSqr(this)));
        entityData.set(FOUND,closest.isPresent());closest.ifPresent(target->{
            if(getBoundingBox().intersects(target.getBoundingBox()))MRProjectileEntity.ignite(target,4);
            var delta=target.position().subtract(position());entityData.set(DX,(float)delta.x);entityData.set(DY,(float)delta.y);entityData.set(DZ,(float)delta.z);
        });
    }
    @Override public boolean isInvisible(){return true;}
    @Override public boolean isInvisibleTo(Player player){return !StandPerception.canSeeStand(player)||!StandPerception.trueSpectator(player)&&super.isInvisible();}
    @Override public void clearFire(){super.clearFire();if(level() instanceof ServerLevel server){
        server.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,getX(),getY(),getZ(),8,.25,.25,.25,0);discard();
    }}
    @Override public boolean hurtServer(ServerLevel server,DamageSource source,float amount){return false;}
    @Override protected void addAdditionalSaveData(ValueOutput out){}
    @Override protected void readAdditionalSaveData(ValueInput in){}
}
