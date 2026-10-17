package com.github.standobyte.jojo.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import java.util.*;

/** Source projectile behaviors share only transport, ownership and persistence. */
public class HamonProjectileEntity extends Projectile {
    public enum Kind {BUBBLE,CUTTER,BUBBLE_CUTTER,BARRIER,TURQUOISE,ZOOM,SNAKE}
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(HamonProjectileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(HamonProjectileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE=SynchedEntityData.defineId(HamonProjectileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(HamonProjectileEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GLIDING=SynchedEntityData.defineId(HamonProjectileEntity.class,EntityDataSerializers.BOOLEAN);
    private float damage,points,speed; private boolean gavePoints,retracting,shot,spendStability;
    private int barrierTicks,barrierMax=100; private UUID snakeTarget;private ItemStack potion=ItemStack.EMPTY;
    private final Set<UUID> hits=new HashSet<>();
    public HamonProjectileEntity(EntityType<? extends HamonProjectileEntity> type,Level level){super(type,level);setNoGravity(true);}
    public HamonProjectileEntity(EntityType<? extends HamonProjectileEntity> type,LivingEntity owner){this(type,owner.level());setOwner(owner);entityData.set(OWNER,owner.getId());setPos(owner.getEyePosition().add(0,kind()==Kind.CUTTER?-.1:-.1,0));setYRot(owner.getYRot());setXRot(owner.getXRot());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(OWNER,0);b.define(COLOR,0x98dac0);b.define(LIFE,100);b.define(RADIUS,.1F);b.define(GLIDING,false);}
    public Kind kind(){if(getType()==HamonEntities.CUTTER)return Kind.CUTTER;if(getType()==HamonEntities.BUBBLE_CUTTER)return Kind.BUBBLE_CUTTER;if(getType()==HamonEntities.BARRIER)return Kind.BARRIER;if(getType()==HamonEntities.TURQUOISE)return Kind.TURQUOISE;if(getType()==HamonEntities.ZOOM)return Kind.ZOOM;if(getType()==HamonEntities.SNAKE)return Kind.SNAKE;return Kind.BUBBLE;}
    public LivingEntity owner(){Entity e=getOwner();if(!(e instanceof LivingEntity))e=level().getEntity(entityData.get(OWNER));return e instanceof LivingEntity l?l:null;}
    public float radius(){return entityData.get(RADIUS);}public int color(){return entityData.get(COLOR);}public boolean gliding(){return entityData.get(GLIDING);}public int lifespan(){return kind()==Kind.BARRIER?(isVehicle()?100:100+barrierMax):entityData.get(LIFE);}
    public float size(float partialTick){return kind()==Kind.BARRIER?Math.min((tickCount+partialTick)/20F,1):1;}
    public HamonProjectileEntity configure(float damage,float points,int duration,float radius){this.damage=damage;this.points=points;entityData.set(LIFE,duration);entityData.set(RADIUS,radius);refreshDimensions();return this;}
    public HamonProjectileEntity gliding(boolean v){entityData.set(GLIDING,v);return this;}
    public HamonProjectileEntity potion(ItemStack stack){potion=stack.copyWithCount(1);var contents=stack.get(DataComponents.POTION_CONTENTS);entityData.set(COLOR,contents==null?0x98dac0:contents.getColor());return this;}
    public HamonProjectileEntity zoom(float speed,boolean spendStability){this.speed=speed;this.spendStability=spendStability;return this;}
    public HamonProjectileEntity barrier(int duration){barrierMax=duration;return this;}
    public HamonProjectileEntity snake(Entity target){snakeTarget=target.getUUID();entityData.set(LIFE,10);return this;}
    public void shootFrom(LivingEntity user,float speed,float spread){if(kind()==Kind.TURQUOISE)setPos(getX(),getY()-radius(),getZ());Vec3 look=user.getLookAngle();double f=.0075F*spread;Vec3 v=look.add(random.nextGaussian()*f,random.nextGaussian()*f,random.nextGaussian()*f).normalize().scale(speed);setDeltaMovement(v);setYRot(user.getYRot());setXRot(user.getXRot());}
    @Override public EntityDimensions getDimensions(Pose pose){return kind()==Kind.TURQUOISE?EntityDimensions.scalable(Math.max(radius()*2,.01F),Math.max(radius()*2,.01F)):super.getDimensions(pose);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> data){super.onSyncedDataUpdated(data);if(data==RADIUS)refreshDimensions();}
    @Override public boolean isPickable(){return false;}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;
        LivingEntity user=owner();
        if(!level().isClientSide() && user!=null)entityData.set(OWNER,user.getId());
        if(!level().isClientSide() && (tickCount>lifespan() || ((kind()==Kind.ZOOM || kind()==Kind.SNAKE) && (user==null || !user.isAlive())))){discard();return;}
        super.tick();
        if(kind()==Kind.SNAKE){tickSnake(user);return;}
        if(kind()==Kind.ZOOM){tickZoom(user);return;}
        if(kind()==Kind.BARRIER && !tickBarrier(user))return;
        Vec3 start=position(),end=start.add(getDeltaMovement());
        if(level() instanceof ServerLevel server){
            if(kind()==Kind.TURQUOISE){
                if(!isInWater()){discard();return;}

                for(var target:server.getEntitiesOfClass(LivingEntity.class,getBoundingBox(),e->e!=user && e.isAlive() && e.isInWater()))hit(target);
            }else{
                BlockHitResult block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                Vec3 until=block.getType()==HitResult.Type.MISS?end:block.getLocation();
                var hit=isVehicle()?null:ProjectileUtil.getEntityHitResult(level(),this,start,until,getBoundingBox().expandTowards(getDeltaMovement()).inflate(1),e->e!=user && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof Projectile p && p.getOwner()==user),.3F);
                if(hit!=null){hit(hit.getEntity());if(kind()!=Kind.BARRIER)discard();}
                else if(block.getType()==HitResult.Type.BLOCK){
                    if(kind()==Kind.BUBBLE_CUTTER && gliding() && block.getDirection().getAxis()==Direction.Axis.Y){
                        Vec3 flat=getDeltaMovement().multiply(1,0,1);setDeltaMovement(flat.lengthSqr()>1E-12?flat.normalize().scale(getDeltaMovement().length()):Vec3.ZERO);
                        end=start.add(getDeltaMovement());
                    }else if(kind()==Kind.BARRIER && isVehicle()){
                        setDeltaMovement(block.getDirection().getAxis()==Direction.Axis.Y?getDeltaMovement().multiply(1,0,1):getDeltaMovement().multiply(0,1,0));
                    }else discard();
                    end=start.add(getDeltaMovement());
                }
            }
        }
        if(!isRemoved())setPos(end);
    }
    private boolean tickBarrier(LivingEntity user){
        if(level().isClientSide())return true;
        if(isVehicle()){
            if(barrierTicks++>=barrierMax){clearRiderStun();discard();return false;}
            if(tickCount%5%2==0 && getFirstPassenger() instanceof LivingEntity l)VampireHamon.damage((ServerLevel)level(),l,this,user,.002F);
        }else if(barrierTicks>0){discard();return false;}
        if(!shot){
            if(user==null || !HamonActionService.state(user).held().equals("caesar_bubble_barrier")){discard();return false;}
            if(HamonActionService.state(user).heldTicks()>=19){shootFrom(user,1,0);shot=true;}else{setPos(user.getEyePosition().add(user.getLookAngle()));return false;}
        }
        return true;
    }
    private void tickZoom(LivingEntity user){
        if(user==null)return;
        double distance=Math.min(tickCount,lifespan()-tickCount)*speed;
        if(retracting)distance=Math.max(0,(lifespan()-tickCount)*speed);
        Vec3 hand=new Vec3(user.getMainArm()==HumanoidArm.LEFT?.35:-.35,-.47,.75).yRot((float)Math.toRadians(-user.getYRot()));
        Vec3 pos=user.getEyePosition().add(hand).add(user.getLookAngle().scale(Math.max(distance,0)));
        if(level() instanceof ServerLevel && !retracting && tickCount<=lifespan()/2){
            var hit=ProjectileUtil.getEntityHitResult(level(),this,position(),pos,getBoundingBox().expandTowards(pos.subtract(position())).inflate(.3),e->e!=user && e.isAlive() && e.isPickable(),.3F);
            if(hit!=null && hit(hit.getEntity())){retracting=true;tickCount=Math.max(tickCount,lifespan()-tickCount);}
            var block=level().clip(new ClipContext(position(),pos,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(block.getType()!=HitResult.Type.MISS){retracting=true;tickCount=Math.max(tickCount,lifespan()-tickCount);}
        }
        setPos(pos);
    }
    private void tickSnake(LivingEntity user){
        if(user==null)return;
        if(tickCount<5){Vec3 v=new Vec3(0,.45,0);Entity target=snakeTarget!=null && level() instanceof ServerLevel s?s.getEntity(snakeTarget):null;
            if(target!=null && target.isAlive()){Vec3 d=target.position().subtract(user.position()).multiply(1,0,1);if(d.lengthSqr()<25){if(d.lengthSqr()>.04)d=d.normalize().scale(.4);v=d.add(0,(target.getBbHeight()+.5)/10,0);}}
            user.setDeltaMovement(v);user.syncVelocity=true;
        }user.fallDistance=0;
    }
    private boolean hit(Entity target){
        LivingEntity user=owner();if(!(level() instanceof ServerLevel level))return false;
        boolean physical=false,hamon=false;
        float physicalDamage=switch(kind()){case CUTTER->2;case BUBBLE_CUTTER->1;case ZOOM->user==null?1:HamonActionService.unarmedDamage(user);default->0;};
        if(physicalDamage>0)physical=target instanceof LivingEntity l?StandDamage.hurt(level,l,damageSources().mobProjectile(this,user),physicalDamage):target.hurtServer(level,damageSources().mobProjectile(this,user),physicalDamage);
        float h=switch(kind()){case BUBBLE,BUBBLE_CUTTER->.3F;case CUTTER->.075F;case BARRIER->.1F;case TURQUOISE->damage*Math.min(2-2F*tickCount/lifespan(),1);case ZOOM->.7F;default->0;};
        if(kind()==Kind.ZOOM && user!=null){
            var state=HamonService.state(user);boolean had=state.energy()>0;
            if(had||spendStability){float e=HamonService.consumeEnergy(user,150,"zoom_punch");hamon=target instanceof LivingEntity l && VampireHamon.damage(level,l,this,user,h*e);
                if(hamon&&had)HamonService.gainActionPoints(user,true,Math.min(150,state.energy())*e);
                if(!gavePoints){HamonService.gainActionPoints(user,true,points);gavePoints=true;}}
        }else if(target instanceof LivingEntity l)hamon=VampireHamon.damage(level,l,this,user,h);
        if(physical && kind()==Kind.ZOOM && user!=null && target instanceof LivingEntity l && user.getAttributeValue(Attributes.ATTACK_KNOCKBACK)>0)HamonActionService.knockback(l,user,(float)user.getAttributeValue(Attributes.ATTACK_KNOCKBACK)*.5F,user.getLookAngle());
        if(physical||hamon){
            if(kind()==Kind.CUTTER && target instanceof LivingEntity living){var effects=potion.get(DataComponents.POTION_CONTENTS);if(effects!=null)for(var effect:effects.getAllEffects()){
                // The Forge source used integer 3/16, hence instantaneous potion power is zero.
                if(effect.getEffect().value().isInstantaneous())effect.getEffect().value().applyInstantaneousEffect(level,this,user,living,effect.getAmplifier(),0);
                else living.addEffect(new MobEffectInstance(effect));}}
            if(kind()==Kind.BARRIER && target instanceof LivingEntity living && living.startRiding(this)){barrierTicks=1;living.addEffect(new MobEffectInstance(VampireCombatService.STUN,barrierMax));setDeltaMovement(0,.05,0);}
            if(kind()==Kind.TURQUOISE && user!=null && target instanceof LivingEntity living)HamonActionService.knockback(living,user,radius()*.1F,getDeltaMovement());
            if(user!=null && kind()!=Kind.ZOOM && (!gavePoints || kind()!=Kind.TURQUOISE)){HamonService.gainActionPoints(user,true,kind()==Kind.BUBBLE || kind()==Kind.BARRIER?12.5F:points);gavePoints=true;}
        }
        return physical||hamon;
    }
    private void clearRiderStun(){for(var e:getPassengers())if(e instanceof LivingEntity l)l.removeEffect(VampireCombatService.STUN);}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide())clearRiderStun();super.remove(reason);}
    @Override protected void positionRider(Entity e,Entity.MoveFunction move){if(hasPassenger(e))move.accept(e,getX(),getY()+(getBbHeight()-e.getBbHeight())/2,getZ());}
    @Override protected void addAdditionalSaveData(ValueOutput o){super.addAdditionalSaveData(o);o.putFloat("Damage",damage);o.putFloat("Points",points);o.putFloat("Radius",radius());o.putInt("Life",lifespan());o.putInt("Color",color());o.putBoolean("Gliding",gliding());o.putBoolean("PointsGiven",gavePoints);o.putBoolean("Retracting",retracting);o.putBoolean("Shot",shot);o.putBoolean("SpendStab",spendStability);o.putInt("BarrierTicks",barrierTicks);o.putInt("BarrierTicksMax",barrierMax);o.putFloat("Speed",speed);o.putInt("Age",tickCount);o.store("Potion",ItemStack.OPTIONAL_CODEC,potion);if(snakeTarget!=null)o.putString("Target",snakeTarget.toString());}
    @Override protected void readAdditionalSaveData(ValueInput i){super.readAdditionalSaveData(i);damage=i.getFloatOr("Damage",0);points=i.getFloatOr("Points",0);entityData.set(RADIUS,i.getFloatOr("Radius",.1F));entityData.set(LIFE,i.getIntOr("Life",100));entityData.set(COLOR,i.getIntOr("Color",0x98dac0));entityData.set(GLIDING,i.getBooleanOr("Gliding",false));gavePoints=i.getBooleanOr("PointsGiven",false);retracting=i.getBooleanOr("Retracting",false);shot=i.getBooleanOr("Shot",false);spendStability=i.getBooleanOr("SpendStab",false);barrierTicks=i.getIntOr("BarrierTicks",0);barrierMax=i.getIntOr("BarrierTicksMax",100);speed=i.getFloatOr("Speed",0);tickCount=i.getIntOr("Age",0);potion=i.read("Potion",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);try{snakeTarget=UUID.fromString(i.getStringOr("Target",""));}catch(IllegalArgumentException ignored){} }
}
