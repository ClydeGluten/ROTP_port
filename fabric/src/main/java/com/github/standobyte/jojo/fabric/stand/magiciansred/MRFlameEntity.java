package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.TimeStopService;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;
import java.util.List;

/** Eight-tick sustained flame. Water/rain extinguishing is active here, unlike the separate fireball. */
public final class MRFlameEntity extends MRProjectileEntity {
    private static final EntityDataAccessor<Long> START_X=SynchedEntityData.defineId(MRFlameEntity.class,EntityDataSerializers.LONG),
            START_Y=SynchedEntityData.defineId(MRFlameEntity.class,EntityDataSerializers.LONG),START_Z=SynchedEntityData.defineId(MRFlameEntity.class,EntityDataSerializers.LONG);
    public MRFlameEntity(EntityType<? extends MRFlameEntity> type,Level level){super(type,level);}
    public MRFlameEntity(MagiciansRedEntity owner){this(MagiciansRedActions.FLAME,owner.level());initializeOwner(owner);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(START_X,0L);b.define(START_Y,0L);b.define(START_Z,0L);}
    @Override public void shoot(double x,double y,double z,float speed,float spread){
        super.shoot(x,y,z,speed,spread);entityData.set(START_X,Double.doubleToLongBits(getX()));entityData.set(START_Y,Double.doubleToLongBits(getY()));entityData.set(START_Z,Double.doubleToLongBits(getZ()));
    }
    public Vec3 startingPosition(){return new Vec3(Double.longBitsToDouble(entityData.get(START_X)),Double.longBitsToDouble(entityData.get(START_Y)),Double.longBitsToDouble(entityData.get(START_Z)));}
    @Override protected Vec3 relativeOffset(){return Vec3.ZERO;}
    @Override protected Vec3 pitchOffset(){return new Vec3(0,.2,0);}
    @Override public int lifespan(){return 8;}
    @Override protected float baseDamage(){return 1;}
    @Override protected float hardness(){return 0;}
    @Override protected float knockbackMultiplier(){return .1F;}
    @Override protected boolean fireDamage(){return true;}
    @Override protected int igniteSeconds(){return 10;}
    @Override protected ClipContext.Block blockMode(){return ClipContext.Block.OUTLINE;}
    @Override public boolean isOnFire(){return false;}
    @Override public boolean canBeEvaded(LivingEntity target){return false;}
    @Override public void tick(){if(TimeStopService.isFrozen(this))return;if(isInWater()||level().isRainingAt(blockPosition()))clearFire();else super.tick();}
    @Override public void clearFire(){super.clearFire();extinguish();}
    @Override protected boolean canBreak(BlockState state,BlockPos pos){return !(state.getBlock() instanceof BaseFireBlock)&&super.canBreak(state,pos);}
    @Override protected void afterBlockHit(ServerLevel server,BlockHitResult hit,boolean destroyed){
        if(!server.getGameRules().get(GameRules.MOB_GRIEFING)||!permitted(server,hit.getBlockPos(),getOwner()))return;
        var pos=hit.getBlockPos();var state=server.getBlockState(pos);
        if(!meltIceAndSnow(server,state,pos)&&!state.getCollisionShape(server,pos).isEmpty())placeFire(server,pos.relative(hit.getDirection()),getOwner());
    }
    @Override protected void breakProjectile(HitResult hit){
        if(hit instanceof BlockHitResult block&&!level().getBlockState(block.getBlockPos()).isCollisionShapeFullBlock(level(),block.getBlockPos()))return;
        super.breakProjectile(hit);
    }
    public static boolean placeFire(ServerLevel server,BlockPos pos,LivingEntity owner){
        return server.isEmptyBlock(pos)&&permitted(server,pos,owner)&&server.setBlockAndUpdate(pos,MagiciansRedFireBlock.BLOCK.getStateForPlacement(server,pos));
    }
    public static boolean meltIceAndSnow(ServerLevel server,BlockState state,BlockPos pos){
        if(!state.is(BlockTags.ICE)&&!state.is(Blocks.SNOW)&&!state.is(Blocks.SNOW_BLOCK))return false;
        if(server.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES)||!state.isCollisionShapeFullBlock(server,pos)){
            RestorationHooks.remember(server,pos,state,server.getBlockEntity(pos),List.of());server.removeBlock(pos,false);
        }else{
            // Source remembers the removal branch only; solid ice replacement has no destruction drops.
            server.setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());server.updateNeighborsAt(pos,Blocks.WATER);
        }
        return true;
    }
}
