package com.github.standobyte.jojo.fabric;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
public final class HamonBlockChargeEntity extends Entity {
    private HamonCharge charge;
    public HamonBlockChargeEntity(EntityType<? extends HamonBlockChargeEntity> t,Level l){super(t,l);noPhysics=true;setNoGravity(true);}
    public HamonBlockChargeEntity(LivingEntity user,BlockPos pos,float damage,int duration,float spent){this(HamonEntities.BLOCK_CHARGE,user.level());setPos(Vec3.atBottomCenterOf(pos));charge=new HamonCharge(damage,duration,duration,user.getUUID().toString(),spent,false,true,false,false);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
    @Override public boolean hurtServer(ServerLevel l,net.minecraft.world.damagesource.DamageSource s,float a){return false;}
    @Override public void tick(){if(TimeStopService.isFrozen(this))return;super.tick();if(!(level() instanceof ServerLevel level))return;BlockPos pos=blockPosition();
        if(charge==null || charge.remaining()<0 || level.isEmptyBlock(pos)){if(level.getBlockState(pos).is(Blocks.COBWEB))level.setBlock(pos,Blocks.TRIPWIRE.defaultBlockState(),3);discard();return;}
        charge=HamonCharges.contact(this,charge,pos,getBoundingBox().inflate(.1));
        if(tickCount==60 && (level.getBlockState(pos).is(Blocks.CACTUS)||level.getBlockState(pos).is(Blocks.POTTED_CACTUS))){for(var target:level.getEntities(this,new net.minecraft.world.phys.AABB(pos).inflate(4))){float amount=.2F*(48-(float)target.distanceToSqr(Vec3.atCenterOf(pos)));target.hurtServer(level,damageSources().cactus(),amount);}level.destroyBlock(pos,false);level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,getX(),getY()+.5,getZ(),1,0,0,0,0);}
    }
    @Override protected void addAdditionalSaveData(ValueOutput o){o.putInt("Age",tickCount);if(charge!=null)o.store("HamonCharge",HamonCharge.CODEC,charge);}
    @Override protected void readAdditionalSaveData(ValueInput i){tickCount=i.getIntOr("Age",0);charge=i.read("HamonCharge",HamonCharge.CODEC).orElse(null);}
}
