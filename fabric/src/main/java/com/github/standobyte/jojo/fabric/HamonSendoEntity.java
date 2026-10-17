package com.github.standobyte.jojo.fabric;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;
/** Expanding axis-aligned waves from the source Sendo Overdrive, with per-wave hit sets. */
public final class HamonSendoEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(HamonSendoEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ARC=SynchedEntityData.defineId(HamonSendoEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AXIS=SynchedEntityData.defineId(HamonSendoEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WAVES=SynchedEntityData.defineId(HamonSendoEntity.class,EntityDataSerializers.INT);
    private UUID ownerId;private net.minecraft.core.BlockPos targetedBlock;private Direction targetedFace;private boolean rawDamage;private float damage,points;private int added,gavePoints;
    private final List<Wave>waves=new ArrayList<>();private final Map<UUID,Integer>lastHit=new HashMap<>();
    private static final class Wave{int age;final Set<UUID>hit=new HashSet<>();}
    public HamonSendoEntity(EntityType<? extends HamonSendoEntity>t,Level l){super(t,l);}
    public HamonSendoEntity(LivingEntity owner,Vec3 center,Direction.Axis axis,float radius,float arc,int count,float damage,float points){this(owner.level(),owner,center,axis,radius,arc,count,damage,points);}
    public HamonSendoEntity(Level level,LivingEntity owner,Vec3 center,Direction.Axis axis,float radius,float arc,int count,float damage,float points){this(HamonEntities.SENDO,level);ownerId=owner==null?null:owner.getUUID();setPos(center);if(owner!=null)setYRot(owner.getYRot());entityData.set(RADIUS,radius);entityData.set(ARC,arc);entityData.set(AXIS,axis.ordinal());entityData.set(WAVES,count);this.damage=damage;this.points=points;}
    public void setBlockTarget(net.minecraft.core.BlockPos pos,Direction face){targetedBlock=pos.immutable();targetedFace=face;}
    public HamonSendoEntity rawDamage(boolean raw){rawDamage=raw;return this;}
    public static HamonSendoEntity molotov(Level level,LivingEntity owner,BlockHitResult hit,float radius,int count,float damage){
        // Source uses ordinary Sendo damage, so live owner strength applies even for charged bottles.
        var wave=new HamonSendoEntity(level,owner,Vec3.atCenterOf(hit.getBlockPos()),hit.getDirection().getAxis(),radius,(float)Math.PI*2,count,damage,0);
        wave.setBlockTarget(hit.getBlockPos(),hit.getDirection());return wave;
    }
    @Override public boolean hurtServer(ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(RADIUS,1F);b.define(ARC,(float)Math.PI*2);b.define(AXIS,1);b.define(WAVES,4);}
    public float radius(){return entityData.get(RADIUS);}public float arc(){return entityData.get(ARC);}public Direction.Axis axis(){return Direction.Axis.values()[entityData.get(AXIS)];}public int waves(){return entityData.get(WAVES);}
    public LivingEntity owner(){return level() instanceof ServerLevel s && ownerId!=null && s.getEntity(ownerId) instanceof LivingEntity l?l:null;}
    private AABB box(double r){return new AABB(position(),position()).inflate(axis()==Direction.Axis.X?1:r,axis()==Direction.Axis.Y?1:r,axis()==Direction.Axis.Z?1:r);}
    @Override public void tick(){if(TimeStopService.isFrozen(this))return;super.tick();setBoundingBox(box(radius()));if(!(level() instanceof ServerLevel level))return;
        if(tickCount>waves()*4+15){discard();return;}
        if(tickCount%4==0 && added++<waves()){waves.add(new Wave());if(added<waves())level.playSound(null,getX(),getY(),getZ(),JojoSounds.get("hamon_spark"),net.minecraft.sounds.SoundSource.AMBIENT,.25F,1+(random.nextFloat()-.5F)*.15F);}
        var owner=owner();
        for(var wave:waves){for(var target:level.getEntitiesOfClass(LivingEntity.class,box(radius()*(wave.age+1)/15D),e->e!=owner && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()))){
            if(!wave.hit.add(target.getUUID()))continue;
            Vec3 delta=target.position().subtract(position());float angle=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            if(targetedFace!=null && targetedFace.getAxis()==Direction.Axis.Y && Math.abs(net.minecraft.util.Mth.wrapDegrees(angle-getYRot()))>Math.toDegrees(arc()/2))continue;
            if(tickCount-lastHit.getOrDefault(target.getUUID(),-100)<4)continue;
            if(rawDamage?VampireHamon.damageRaw(level,target,this,owner,damage):VampireHamon.damage(level,target,this,owner,damage)){lastHit.put(target.getUUID(),tickCount);if(gavePoints++<6 || gavePoints%4==0)if(owner!=null)HamonService.gainActionPoints(owner,true,points*.25F);}
        }wave.age++;}waves.removeIf(w->w.age>=15);
    }
    @Override protected void addAdditionalSaveData(ValueOutput o){if(ownerId!=null)o.putString("Owner",ownerId.toString());o.putFloat("Radius",radius());o.putFloat("Arc",arc());o.putInt("Axis",axis().ordinal());o.putInt("WaveCount",waves());o.putFloat("Damage",damage);o.putBoolean("RawDamage",rawDamage);if(targetedBlock!=null)o.putLong("Target",targetedBlock.asLong());if(targetedFace!=null)o.putInt("Face",targetedFace.ordinal());o.putFloat("Points",points);o.putInt("Added",added);o.putInt("GavePoints",gavePoints);o.putInt("Age",tickCount);o.putInt("ActiveWaves",waves.size());for(int n=0;n<waves.size();n++){Wave w=waves.get(n);o.putInt("WaveAge"+n,w.age);o.putString("WaveHit"+n,w.hit.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));}}
    @Override protected void readAdditionalSaveData(ValueInput i){try{ownerId=UUID.fromString(i.getStringOr("Owner",""));}catch(IllegalArgumentException ignored){}entityData.set(RADIUS,i.getFloatOr("Radius",1));entityData.set(ARC,i.getFloatOr("Arc",(float)Math.PI*2));entityData.set(AXIS,Math.clamp(i.getIntOr("Axis",1),0,2));entityData.set(WAVES,i.getIntOr("WaveCount",4));damage=i.getFloatOr("Damage",0);rawDamage=i.getBooleanOr("RawDamage",false);long savedTarget=i.getLongOr("Target",Long.MIN_VALUE);targetedBlock=savedTarget==Long.MIN_VALUE?null:net.minecraft.core.BlockPos.of(savedTarget);int savedFace=i.getIntOr("Face",-1);targetedFace=savedFace<0?null:Direction.values()[Math.clamp(savedFace,0,5)];points=i.getFloatOr("Points",0);added=i.getIntOr("Added",0);gavePoints=i.getIntOr("GavePoints",0);tickCount=i.getIntOr("Age",0);waves.clear();for(int n=0;n<Math.min(8,i.getIntOr("ActiveWaves",0));n++){Wave w=new Wave();w.age=i.getIntOr("WaveAge"+n,0);for(String id:i.getStringOr("WaveHit"+n,"").split(","))try{w.hit.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}waves.add(w);}}
}
