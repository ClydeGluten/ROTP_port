package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.List;

/** Original large ankh; special changes size, target steering, contact damage and explosion semantics. */
public class MRCrossfireEntity extends MRProjectileEntity {
    private static final EntityDataAccessor<Float> SCALE=SynchedEntityData.defineId(MRCrossfireEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TARGET=SynchedEntityData.defineId(MRCrossfireEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> TX=SynchedEntityData.defineId(MRCrossfireEntity.class,EntityDataSerializers.LONG),
            TY=SynchedEntityData.defineId(MRCrossfireEntity.class,EntityDataSerializers.LONG),TZ=SynchedEntityData.defineId(MRCrossfireEntity.class,EntityDataSerializers.LONG);
    public MRCrossfireEntity(EntityType<? extends MRCrossfireEntity> type,Level level){super(type,level);}
    public MRCrossfireEntity(MagiciansRedEntity owner){this(MagiciansRedActions.CROSSFIRE_ENTITY,owner.level());initializeOwner(owner,false);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(SCALE,1F);b.define(TARGET,false);b.define(TX,0L);b.define(TY,0L);b.define(TZ,0L);}
    public boolean small(){return false;}
    public float scale(){return entityData.get(SCALE);}
    public void setScale(float value){if(!Float.isFinite(value)||value<=0||value>1)throw new IllegalArgumentException("Invalid ankh scale");entityData.set(SCALE,value);refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose pose){return super.getDimensions(pose).scale(entityData==null?1:scale());}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(SCALE))refreshDimensions();}
    public void setTarget(Vec3 target){entityData.set(TARGET,true);entityData.set(TX,Double.doubleToLongBits(target.x));entityData.set(TY,Double.doubleToLongBits(target.y));entityData.set(TZ,Double.doubleToLongBits(target.z));}
    public Vec3 target(){return entityData.get(TARGET)?new Vec3(Double.longBitsToDouble(entityData.get(TX)),Double.longBitsToDouble(entityData.get(TY)),Double.longBitsToDouble(entityData.get(TZ))):null;}
    @Override public int lifespan(){return 100;}
    @Override protected float baseDamage(){return (small()?2:6)*scale();}
    @Override protected float hardness(){return 0;}
    @Override protected boolean fireDamage(){return true;}
    @Override public boolean isOnFire(){return false;}
    @Override public boolean ignoreExplosion(Explosion explosion){return true;}
    @Override public boolean hurtServer(ServerLevel server,DamageSource source,float amount){return !source.is(DamageTypeTags.IS_EXPLOSION)&&super.hurtServer(server,source,amount);}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;
        if(isInWater()||level().isRainingAt(blockPosition()))clearFire();else{burnBlocksTick();super.tick();}
    }
    @Override public void clearFire(){super.clearFire();extinguish();}
    @Override protected void moveProjectile(){
        super.moveProjectile();var target=target();if(target==null)return;
        double speedSqr=getDeltaMovement().lengthSqr();if(speedSqr<=0)return;
        var delta=target.subtract(position());double distanceSqr=delta.lengthSqr();
        if(speedSqr<distanceSqr)setDeltaMovement(getDeltaMovement().scale(distanceSqr/speedSqr).add(delta).normalize().scale(Math.sqrt(speedSqr)));
        else if(!level().isClientSide())explode();
    }
    @Override protected void afterEntityHit(EntityHitResult hit,boolean hurt){explode();}
    @Override protected void afterBlockHit(ServerLevel server,BlockHitResult hit,boolean destroyed){explode();}
    private void explode(){if(level() instanceof ServerLevel server&&!isRemoved())MRCrossfireExplosion.explode(server,this);}
    private void burnBlocksTick(){
        if(!(level() instanceof ServerLevel server)||small()||!server.getGameRules().get(JojoGameRules.BREAK_BLOCKS))return;
        var bounds=getBoundingBox().move(getDeltaMovement()).inflate(.5);var min=BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ);var max=BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ);
        for(int x=min.getX();x<=max.getX();x++)for(int y=min.getY();y<=max.getY();y++)for(int z=min.getZ();z<=max.getZ();z++){
            var pos=new BlockPos(x,y,z);var state=server.getBlockState(pos);if(!permitted(server,pos,getOwner())||MRFlameEntity.meltIceAndSnow(server,state,pos))continue;
            var fire=FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
            if(fire!=null&&fire.getBurnOdds()>0){catchFire(server,pos,state,getOwner());RestorationHooks.remember(server,pos,state,server.getBlockEntity(pos),List.of());server.removeBlock(pos,false);}
        }
        for(var direction:Direction.values()){
            int x1=min.getX(),x2=max.getX(),y1=min.getY(),y2=max.getY(),z1=min.getZ(),z2=max.getZ();
            switch(direction){case DOWN->y2=y1;case UP->y1=y2;case NORTH->z2=z1;case SOUTH->z1=z2;case WEST->x2=x1;case EAST->x1=x2;}
            igniteFace(server,new BlockPos(x1,y1,z1),new BlockPos(x2,y2,z2),direction);
        }
    }
    private void igniteFace(ServerLevel server,BlockPos min,BlockPos max,Direction direction){
        for(int x=min.getX();x<=max.getX();x++)for(int y=min.getY();y<=max.getY();y++)for(int z=min.getZ();z<=max.getZ();z++){
            var pos=new BlockPos(x,y,z);if(!server.isEmptyBlock(pos))continue;
            var user=user();if(user!=null&&user.getBoundingBox().intersects(new AABB(pos)))return;
            var adjacent=pos.relative(direction);if(!server.getBlockState(adjacent).getCollisionShape(server,adjacent).isEmpty())MRFlameEntity.placeFire(server,pos,getOwner());
        }
    }
}
