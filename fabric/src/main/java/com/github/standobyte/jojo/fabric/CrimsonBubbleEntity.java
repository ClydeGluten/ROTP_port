package com.github.standobyte.jojo.fabric;
import com.github.standobyte.jojo.core.HamonProgress;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
/** Original Last Hamon inheritance bubble, including its gradual decay and retaliation. */
public final class CrimsonBubbleEntity extends Entity {
    private int strength,control;private Vec3 initial;
    public CrimsonBubbleEntity(EntityType<?> type,Level level){super(type,level);setDeltaMovement(randomMotion().normalize().scale(.002));}
    public CrimsonBubbleEntity(Level level){this(HamonContentEntities.CRIMSON_BUBBLE,level);}
    public void setHamonPoints(int strength,int control){this.strength=Math.max(0,strength);this.control=Math.max(0,control);}
    public int strengthPoints(){return strength;}public int controlPoints(){return control;}
    public void putItem(ItemEntity item){item.setPickUpDelay(2);item.setExtendedLifetime();item.startRiding(this);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){}
    private Vec3 randomMotion(){return new Vec3(random.nextDouble()-.5,(random.nextDouble()-.5)*.5,random.nextDouble()-.5);}
    @Override public void tick(){super.tick();
        if(level() instanceof ServerLevel level){
            if(strength==0&&control==0){discard();return;}
            for(Entity rider:getPassengers())if(rider instanceof ItemEntity item)item.setPickUpDelay(2);
            for(var living:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox(),e->e.isAlive()&&!e.isSpectator()&&!(e instanceof Player p&&p.isCreative()))){
                if(HamonService.hasHamon(living)){
                    var old=HamonService.state(living);HamonService.setStats(living,old.strengthPoints()+(int)(strength*.8),old.controlPoints()+(int)(control*.8));
                    VampireHamonParticles.emit(living,2);if(old.technique().equals("joseph"))ClackersItem.voice(living,"joseph_crimson_bubble_reaction");
                    if(living instanceof ServerPlayer player)HamonAdvancements.onLastHamon(player,this);
                    strength=0;control=0;
                }else{VampireHamon.damage(level,living,this,null,damage());strength=Math.max(0,strength-1);control=Math.max(0,control-1);}
            }
            if(strength>0&&random.nextFloat()<.2F)strength--;if(control>0&&random.nextFloat()<.2F)control--;
            if(initial==null)initial=position();else if(tickCount%100==0||position().distanceToSqr(initial)>2.25)setDeltaMovement(initial.subtract(position()).add(randomMotion()).normalize().scale(.002));
        }
        move(MoverType.SELF,getDeltaMovement());
    }
    private float damage(){return(strength+control)*20F/HamonProgress.MAX_HAMON_POINTS;}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){
        if(isInvulnerableToBase(source))return false;
        if(source.getEntity() instanceof Player p&&p.isCreative()){strength=control=0;}
        else{if(source.getDirectEntity() instanceof LivingEntity living)VampireHamon.damage(level,living,this,null,damage());
            strength=Math.max(0,strength-(int)amount);control=Math.max(0,control-(int)amount);}
        return true;
    }
    @Override public boolean isPickable(){return true;}
    @Override protected void positionRider(Entity rider,MoveFunction move){move.accept(rider,getX(),getY(),getZ());}
    @Override protected void addAdditionalSaveData(ValueOutput out){out.putInt("StrengthPoints",strength);out.putInt("ControlPoints",control);if(initial!=null)out.store("InitialPoint",Vec3.CODEC,initial);}
    @Override protected void readAdditionalSaveData(ValueInput in){strength=in.getIntOr("StrengthPoints",0);control=in.getIntOr("ControlPoints",0);initial=in.read("InitialPoint",Vec3.CODEC).orElse(null);}
}
