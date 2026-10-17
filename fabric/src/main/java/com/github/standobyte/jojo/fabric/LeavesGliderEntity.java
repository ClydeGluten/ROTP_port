package com.github.standobyte.jojo.fabric;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
/** Original four-rider glider physics and shared passenger energy, driven by server input. */
public final class LeavesGliderEntity extends Entity {
    public static final float MAX_ENERGY=200,MAX_HEALTH=4;
    private static final EntityDataAccessor<Boolean> FLYING=SynchedEntityData.defineId(LeavesGliderEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ENERGY=SynchedEntityData.defineId(LeavesGliderEntity.class,EntityDataSerializers.FLOAT),HEALTH=SynchedEntityData.defineId(LeavesGliderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> CHARGERS=SynchedEntityData.defineId(LeavesGliderEntity.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockState> LEAVES=SynchedEntityData.defineId(LeavesGliderEntity.class,EntityDataSerializers.BLOCK_STATE);
    private float passengersHeight,yRotDelta;
    public LeavesGliderEntity(EntityType<?> type,Level level){super(type,level);}
    @Override public boolean causeFallDamage(double distance,float multiplier,DamageSource source){return false;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){data.define(FLYING,false);data.define(ENERGY,MAX_ENERGY);data.define(HEALTH,MAX_HEALTH);data.define(CHARGERS,(byte)0);data.define(LEAVES,Blocks.OAK_LEAVES.defaultBlockState());}
    @Override protected InterpolationHandler createInterpolationHandler(){return LinearInterpolationHandler.create(this,10);}
    public void setEnergy(float value){entityData.set(ENERGY,Mth.clamp(value,0,MAX_ENERGY));}
    public float getEnergy(){return entityData.get(ENERGY);}
    public float getHealth(){return entityData.get(HEALTH);}
    public void setHealth(float value){entityData.set(HEALTH,Mth.clamp(value,0,MAX_HEALTH));}
    public boolean isFlying(){return entityData.get(FLYING);}
    public float getYRotDelta(){return yRotDelta;}
    public BlockState getLeavesBlock(){return entityData.get(LEAVES);}
    public void setLeavesBlock(BlockState state){if(!state.isAir())entityData.set(LEAVES,state);}
    public boolean isCharging(int seat){return (entityData.get(CHARGERS)&(1<<seat))!=0;}
    @Override public void tick(){super.tick();
        if(level().isClientSide()){getInterpolation().interpolate();return;}
        boolean flying=!onGround()&&!isInWater();
        if(isFlying()&&!flying){setDeltaMovement(Vec3.ZERO);ejectPassengers();}
        entityData.set(FLYING,flying);
        if(flying){Vec3 horizontal=getDeltaMovement().multiply(1,0,1);
            if(isVehicle()){
                boolean left=false,right=false;
                if(getFirstPassenger() instanceof ServerPlayer player){left=player.getLastClientInput().left();right=player.getLastClientInput().right();}
                float delta=3.5F-getPassengers().size()*.5F;
                if(left||right)yRotDelta=(left?-delta:0)+(right?delta:0);
                else yRotDelta=yRotDelta>0?Math.max(yRotDelta-delta*.05F,0):Math.min(yRotDelta+delta*.05F,0);
                setYRot(getYRot()+yRotDelta);
                for(Entity rider:getPassengers()){rider.setYRot(rider.getYRot()+yRotDelta);if(rider instanceof LivingEntity living)living.yBodyRot+=yRotDelta;}
                horizontal=Vec3.directionFromRotation(0,getYRot()).scale(horizontal.length());
            }
            Vec3 motion=horizontal.normalize().scale(Math.min(horizontal.length()+.01,.5));
            double gravity=isNoGravity()?0:-.01*(1+getPassengers().size());
            setDeltaMovement(motion.x,Math.max(getDeltaMovement().y,0)+gravity,motion.z);move(MoverType.SELF,getDeltaMovement());
        }
        recharge();setHealth(getHealth()+(getEnergy()<=0?-.04F:.1F));
        if(getHealth()<=0){breakLeaves(200);discard();}
        else if(getEnergy()>0&&random.nextFloat()<getEnergy()/MAX_ENERGY*.2F)VampireHamonParticles.emit(this,getEnergy()/MAX_ENERGY*.1F);
    }
    private void recharge(){
        var users=new ArrayList<LivingEntity>();byte charging=0;
        for(Entity rider:getPassengers())if(rider instanceof LivingEntity living&&HamonService.hasHamon(living))users.add(living);
        boolean creative=false;
        for(LivingEntity user:users)if(user instanceof Player p&&p.isCreative()){creative=true;charging|=(byte)(1<<getPassengers().indexOf(user));}
        if(creative)setEnergy(MAX_ENERGY);
        else{setEnergy(Math.max(getEnergy()-2,0));float needed=MAX_ENERGY-getEnergy();
            var available=new ArrayList<>(users);
            while(needed>0&&!available.isEmpty()){
                float share=needed/available.size(),before=needed;
                for(var iterator=available.iterator();iterator.hasNext();){LivingEntity user=iterator.next();
                    float consumed=Math.min(share,HamonService.state(user).energy());
                    if(consumed>0){HamonService.gainActionPoints(user,false,consumed);HamonService.consumeResourceEnergy(user,consumed);needed-=consumed;charging|=(byte)(1<<getPassengers().indexOf(user));}
                    if(consumed<share)iterator.remove();
                }
                if(needed>=before)break;
            }
            setEnergy(MAX_ENERGY-needed);
        }
        entityData.set(CHARGERS,charging);
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand,Vec3 hit){
        if(player.isSecondaryUseActive()||player.getVehicle()==this)return InteractionResult.PASS;
        return level().isClientSide()?InteractionResult.SUCCESS:player.startRiding(this)?InteractionResult.CONSUME:InteractionResult.PASS;
    }
    @Override protected boolean canAddPassenger(Entity entity){return getPassengers().size()<4;}
    // Server owns flight; vanilla must not accept client vehicle-position packets for this entity.
    @Override public LivingEntity getControllingPassenger(){return null;}
    @Override protected void addPassenger(Entity rider){
        if(!isVehicle()){
            setXRot(rider.getXRot());setYRot(rider.getYRot());rider.setYBodyRot(rider.getYRot());
            double gap=-Entity.collideBoundingBox(this,new Vec3(0,-1,0),getBoundingBox(),level(),java.util.List.of()).y;
            if(gap<1)move(MoverType.SELF,new Vec3(0,rider.getBbHeight()+1-gap,0));
            Vec3 forward=Vec3.directionFromRotation(0,getYRot());setDeltaMovement(forward.scale(Math.max(rider.getDeltaMovement().multiply(1,0,1).dot(forward),.05)));
        }
        super.addPassenger(rider);updateHeight();
    }
    @Override protected void removePassenger(Entity rider){super.removePassenger(rider);updateHeight();}
    private void updateHeight(){passengersHeight=(float)getPassengers().stream().mapToDouble(Entity::getBbHeight).max().orElse(0);refreshDimensions();}
    @Override public EntityDimensions getDimensions(Pose pose){var base=super.getDimensions(pose);return EntityDimensions.scalable(base.width(),base.height()+passengersHeight);}
    private static final Vec3[] OFFSETS={new Vec3(0,0,.625),new Vec3(.625,0,0),new Vec3(-.625,0,0),new Vec3(0,0,-.625)};
    @Override protected void positionRider(Entity rider,MoveFunction move){int seat=getPassengers().indexOf(rider);if(seat<0||seat>=4)return;
        Vec3 offset=OFFSETS[seat].yRot(-getYRot()*Mth.DEG_TO_RAD);move.accept(rider,getX()+offset.x,getY()+passengersHeight-rider.getBbHeight(),getZ()+offset.z);}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){
        Entity direct=source.getDirectEntity();if(isInvulnerableToBase(source)||"hamon".startsWith(source.getMsgId())||direct!=null&&direct.getVehicle()==this)return false;
        if(direct instanceof LivingEntity living){float energy=Math.min(getEnergy(),100);VampireHamon.damage(level,living,this,null,energy*.04F);setEnergy(getEnergy()-energy);}
        setHealth(getHealth()-amount);breakLeaves(Math.max(1,(int)(amount*100)));needsSync=true;return true;
    }
    private void breakLeaves(int count){if(level() instanceof ServerLevel level){level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,getLeavesBlock()),getX(),getY(1),getZ(),count,getBbWidth()*.5,0,getBbWidth()*.5,0);
        var sound=getLeavesBlock().getSoundType();level.playSound(null,getX(),getY(1),getZ(),getHealth()<=0?sound.getBreakSound():sound.getHitSound(),getSoundSource(),(sound.getVolume()+1)/(getHealth()<=0?2:8),sound.getPitch()*.8F);}}
    @Override public void push(Entity other){}
    @Override public boolean isPickable(){return true;}
    @Override protected void readAdditionalSaveData(ValueInput in){entityData.set(FLYING,in.getBooleanOr("Flight",false));setEnergy(in.getFloatOr("Energy",MAX_ENERGY));setHealth(in.getFloatOr("Health",MAX_HEALTH));setLeavesBlock(in.read("Block",BlockState.CODEC).orElse(Blocks.OAK_LEAVES.defaultBlockState()));}
    @Override protected void addAdditionalSaveData(ValueOutput out){out.putBoolean("Flight",isFlying());out.putFloat("Energy",getEnergy());out.putFloat("Health",getHealth());out.store("Block",BlockState.CODEC,getLeavesBlock());}
}
