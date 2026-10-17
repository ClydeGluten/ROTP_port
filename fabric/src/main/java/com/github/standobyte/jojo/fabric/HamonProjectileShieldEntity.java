package com.github.standobyte.jojo.fabric;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.UUID;
/** Fixed-angle 8x4 rectangle whose plane follows its owner, as in the Forge shield. */
public final class HamonProjectileShieldEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(HamonProjectileShieldEntity.class,EntityDataSerializers.INT);
    private UUID ownerId;
    public HamonProjectileShieldEntity(EntityType<? extends HamonProjectileShieldEntity>t,Level l){super(t,l);}
    public HamonProjectileShieldEntity(LivingEntity owner){this(HamonEntities.SHIELD,owner.level());ownerId=owner.getUUID();entityData.set(OWNER,owner.getId());setXRot(owner.getXRot());setYRot(owner.getYRot());update(owner);}
    @Override public boolean hurtServer(ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,0);}
    public LivingEntity owner(){Entity e=level().getEntity(entityData.get(OWNER));if(e==null && level() instanceof ServerLevel s && ownerId!=null)e=s.getEntity(ownerId);return e instanceof LivingEntity l?l:null;}
    public float width(){return 8;}public float height(){return 4;}
    private Vec3 normal(){return Vec3.directionFromRotation(getXRot(),getYRot());}
    private void update(LivingEntity user){setPos(user.position().add(0,user.getBbHeight()*.5-2,0).add(normal().scale(2)));}
    @Override public void tick(){if(TimeStopService.isFrozen(this))return;super.tick();var user=owner();if(user==null || !user.isAlive() || !level().isClientSide() && !HamonActionService.state(user).held().equals("hamon_projectile_shield")){if(!level().isClientSide())discard();return;}update(user);
        if(!(level() instanceof ServerLevel level))return;
        Vec3 center=position().add(0,2,0),n=normal(),right=new Vec3(n.z,0,-n.x).normalize(),up=n.cross(right);
        for(var p:level.getEntitiesOfClass(Projectile.class,getBoundingBox().inflate(24),Entity::isAlive)){
            if(p instanceof HamonProjectileEntity hp && hp.kind()==HamonProjectileEntity.Kind.TURQUOISE)continue;
            Vec3 velocity=p.getDeltaMovement();double divisor=velocity.dot(n);if(Math.abs(divisor)<1E-8)continue;
            double fraction=center.subtract(p.position()).dot(n)/divisor;if(fraction<0 || fraction>1)continue;
            Vec3 point=p.position().add(velocity.scale(fraction)),local=point.subtract(center);
            if(Math.abs(local.dot(right))>4 || Math.abs(local.dot(up))>2)continue;
            if(level.clip(new ClipContext(p.position(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            float cost=(float)velocity.length()*20;
            if(HamonService.state(user).energy()<cost){HamonService.energy(user,0);discard();return;}
            HamonService.consumeEnergy(user,cost,"projectile_shield");HamonService.gainActionPoints(user,false,cost);
            p.setDeltaMovement(velocity.reverse());p.move(MoverType.SELF,p.getDeltaMovement());p.syncVelocity=true;
            VampireHamonParticles.emit(user,5);
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput o){if(ownerId!=null)o.putString("Owner",ownerId.toString());}
    @Override protected void readAdditionalSaveData(ValueInput i){try{ownerId=UUID.fromString(i.getStringOr("Owner",""));}catch(IllegalArgumentException ignored){}}
}
