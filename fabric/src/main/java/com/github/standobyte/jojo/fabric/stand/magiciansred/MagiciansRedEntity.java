package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.core.*;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Original MR task state, fire attacks, owner control and inherited Stand defenses. */
public final class MagiciansRedEntity extends Mob implements StandManifestation {
    public enum Task { NONE,LIGHT,HEAVY,KICK,FLAME_BURST,FIREBALL,CROSSFIRE,CROSSFIRE_SPECIAL,BLOCK,RED_BIND }
    private static final EntityDataAccessor<Integer> OWNER=dataInt(),TASK=dataInt(),PHASE=dataInt(),TICKS=dataInt(),DURATION=dataInt(),
            SUMMON_MODE=dataInt(),SUMMON_LOCK=dataInt(),WEAKNESS=dataInt(),UNSUMMON=dataInt(),FADE=dataInt(),VARIANT=dataInt(),AGE=dataInt(),BOUND=dataInt();
    private static final EntityDataAccessor<Boolean> CONTROL=dataBool(),LEFT=dataBool(),SUMMON_ANIMATION=dataBool();
    private static final EntityDataAccessor<Float> FINISHER=SynchedEntityData.defineId(MagiciansRedEntity.class,EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    private int elapsed,duration,recovery,heldLease,controlLease,decay;
    private float finisher,heavyFinisher,guardDamage;
    private boolean previousLight,firstPerform,transferringDamage,retainedBind,nextOffHand=true,additionalOffHand;
    private int lastSwing=-2;
    private Task queued;
    private Entity attackTarget;
    private Vec3 aimPoint,anchor,input=Vec3.ZERO;
    private float controlYaw,controlPitch;
    private final Map<UUID,Integer> heavyHitCooldowns=new HashMap<>();
    private static EntityDataAccessor<Integer> dataInt(){return SynchedEntityData.defineId(MagiciansRedEntity.class,EntityDataSerializers.INT);}
    private static EntityDataAccessor<Boolean> dataBool(){return SynchedEntityData.defineId(MagiciansRedEntity.class,EntityDataSerializers.BOOLEAN);}
    public MagiciansRedEntity(EntityType<? extends MagiciansRedEntity> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);noPhysics=true;}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE,11).add(Attributes.ATTACK_SPEED,11).add(Attributes.MOVEMENT_SPEED,.65);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        super.defineSynchedData(b);b.define(OWNER,-1);b.define(TASK,0);b.define(PHASE,0);b.define(TICKS,0);b.define(DURATION,0);b.define(SUMMON_MODE,0);
        b.define(SUMMON_LOCK,7);b.define(WEAKNESS,0);b.define(UNSUMMON,-1);b.define(FADE,7);b.define(VARIANT,0);b.define(AGE,0);b.define(BOUND,-1);
        b.define(CONTROL,false);b.define(LEFT,false);b.define(SUMMON_ANIMATION,true);b.define(FINISHER,0F);
    }
    @Override public Mob entity(){return this;}
    @Override public StandDefinitions.Definition definition(){return StandDefinitions.definition(MagiciansRedActions.ID);}
    @Override public void setOwner(ServerPlayer player){ownerUuid=player.getUUID();entityData.set(OWNER,player.getId());setPos(player.position());setRot(player.getYRot(),player.getXRot());entityData.set(VARIANT,random.nextInt(128));entityData.set(LEFT,player.getMainArm()==HumanoidArm.LEFT);updateAttributes();}
    @Override public ServerPlayer owner(){return ownerUuid!=null&&level().getEntity(ownerUuid) instanceof ServerPlayer player?player:null;}
    public LivingEntity user(){return level().getEntity(ownerId()) instanceof LivingEntity living?living:null;}
    @Override public int ownerId(){return entityData.get(OWNER);}
    public Task task(){return Task.values()[entityData.get(TASK)];}
    @Override public int actionPhase(){return entityData.get(PHASE);}
    @Override public int actionTicks(){return entityData.get(TICKS);}
    @Override public int actionDuration(){return entityData.get(DURATION);}
    @Override public int abilityKind(){return task().ordinal();}
    @Override public boolean heavyAttack(){return task()==Task.HEAVY||task()==Task.KICK;}
    @Override public float finisherCharge(){return entityData.get(FINISHER);}
    @Override public int uiRecoveryTicks(){return actionPhase()==3||actionPhase()==6?Math.max(0,actionDuration()-actionTicks()):0;}
    @Override public boolean allowsStaminaRegen(){return actionPhase()==0||actionPhase()==3;}
    @Override public HumanoidArm getMainArm(){return user()==null?HumanoidArm.RIGHT:user().getMainArm();}
    public int summonMode(){return entityData.get(SUMMON_MODE);}
    public int summonVariant(){return entityData.get(VARIANT);}
    public boolean isArmsOnlyMode(){return (summonMode()&1)!=0;}
    public boolean summonAnimation(){return entityData.get(SUMMON_ANIMATION);}
    public boolean leftPunch(){return entityData.get(LEFT);}
    public int visualAge(){return entityData.get(AGE);}
    public int boundTargetId(){return entityData.get(BOUND);}
    private boolean armsWeakened(){return isArmsOnlyMode()||entityData.get(WEAKNESS)>0;}
    private float development(){return user()==null?0:user().getAttachedOrCreate(StandAttachments.PROGRESS).development();}
    public double staminaCondition(){
        if(!(user() instanceof Player player)||StandConfig.staminaFree(player)||StandAttachments.resolve(player).isBurst())return 1;
        var state=player.getAttachedOrCreate(StandAttachments.PROGRESS);return .25+Math.min(state.stamina()/state.maxStamina()*1.5,.75);
    }
    public double efficiency(){return staminaCondition()*(user()==null?1:StandStatFormulas.rangeStrengthFactor(5,10,distanceTo(user())));}
    public double attackDamage(){return definition().stats().power(development())*efficiency()*(armsWeakened()?.75:1);}
    public double attackSpeed(){return definition().stats().speed(development())*efficiency();}
    public double durability(){return definition().stats().durability(development())*efficiency()*(armsWeakened()?.75:1);}
    public double precision(){return armsWeakened()?0:definition().stats().precision(development())*efficiency();}
    public float projectileInaccuracy(float base){return (float)Math.max((base+1)*8/Math.max(precision(),4)-1,0);}
    private void updateAttributes(){
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(definition().stats().power());getAttribute(Attributes.ATTACK_SPEED).setBaseValue(definition().stats().speed());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.1+definition().stats().speed(development())*.05);
    }
    public void prepareActionSummon(Task action,boolean fresh){
        int requested=action==Task.LIGHT?7:action==Task.BLOCK?15:0;
        if(fresh&&requested!=0){entityData.set(SUMMON_MODE,requested);entityData.set(SUMMON_LOCK,0);entityData.set(SUMMON_ANIMATION,false);}
        else if(isArmsOnlyMode()){
            if(requested==0){entityData.set(SUMMON_MODE,2);int ticks=StandStatFormulas.getSummonLockTicks(attackSpeed());entityData.set(WEAKNESS,ticks);entityData.set(FADE,ticks);entityData.set(UNSUMMON,-1);}
            else entityData.set(SUMMON_MODE,summonMode()|requested);
        }
    }
    public float renderAlpha(){int ticks=Math.max(entityData.get(SUMMON_LOCK),entityData.get(WEAKNESS)),total=entityData.get(FADE);float alpha=ticks>0&&total>0?1-ticks/(float)total:1;return Mth.clamp(entityData.get(UNSUMMON)>=0?alpha*(1-entityData.get(UNSUMMON)/(float)Math.max(1,total)):alpha,0,1);}
    private boolean live(){return owner()!=null&&owner().isAlive()&&!owner().isSpectator()&&owner().level()==level()&&owner().getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(MagiciansRedActions.ID);}
    private static boolean held(Task task){return task==Task.FLAME_BURST||task==Task.RED_BIND||task==Task.BLOCK||charged(task);}
    private static boolean charged(Task task){return task==Task.CROSSFIRE||task==Task.CROSSFIRE_SPECIAL;}
    public boolean canUse(Task next){return next!=Task.NONE&&live()&&!TimeStopService.isFrozen(this)&&!VampireCombatService.isStunned(this)&&!VampireCombatService.isStunned(owner())&&actionPhase()!=6&&entityData.get(UNSUMMON)<0&&(task()!=next||held(next)||next==Task.LIGHT);}
    private boolean canCancel(Task next){return task()==Task.NONE||held(task())||actionPhase()==3&&(task()==Task.KICK||task()==Task.LIGHT);}
    public boolean start(Task next){
        if(!canUse(next))return false;if(task()==next&&held(next)){refreshHeld();return true;}
        if(!canCancel(next)){if(!held(next))queued=next;return false;}
        var hit=aim(2.5);attackTarget=hit instanceof EntityHitResult e?e.getEntity():null;aimPoint=hit.getType()==HitResult.Type.MISS?null:hit.getLocation();
        retainedBind=false;entityData.set(SUMMON_ANIMATION,false);entityData.set(TASK,next.ordinal());elapsed=0;recovery=0;firstPerform=true;heldLease=12;
        switch(next){
            case LIGHT->{alternateHands();int windup=StandStatFormulas.getLightAttackWindup(attackSpeed(),finisher,Math.min(guardDamage/5,1),!previousLight,random::nextDouble);int perform=StandStatFormulas.getLightAttackRecovery(attackSpeed(),finisher);recovery=perform*(isArmsOnlyMode()?2:4);phase(windup>0?1:2,windup>0?windup:perform);}
            case HEAVY,KICK->{alternateHands();heavyFinisher=finisher;finisher=Math.max(0,finisher-.51F);int windup=StandStatFormulas.getHeavyAttackWindup(attackSpeed(),heavyFinisher);recovery=StandStatFormulas.getHeavyAttackRecovery(attackSpeed(),heavyFinisher);phase(windup>0?1:2,windup>0?windup:1);
                if(next==Task.KICK){var bind=landedBind();if(bind!=null){attackTarget=bind.attached();bind.setKickAttack();playOriginal("avdol_hell_2_u");}}
            }
            case FLAME_BURST,RED_BIND->{phase(2,Integer.MAX_VALUE);if(next==Task.RED_BIND){if(finisher<.5F)playOriginal("avdol_red_bind");playOriginal("magicians_red_fire_ability");}}
            case CROSSFIRE,CROSSFIRE_SPECIAL->{phase(4,20);playOriginal(next==Task.CROSSFIRE?"avdol_crossfire_hurricane":"avdol_crossfire_hurricane_special");playOriginal("magicians_red_fire_ability");}
            case BLOCK->{guardDamage=0;phase(5,Integer.MAX_VALUE);}
            case FIREBALL->{phase(2,3);StandPerception.playSound(this,net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,1,1);}
            default->throw new IllegalStateException("No task");
        }
        previousLight=next==Task.LIGHT;sync();return true;
    }
    private void alternateHands(){
        boolean off;if(tickCount-lastSwing>1){nextOffHand=!nextOffHand;off=nextOffHand;}else{off=additionalOffHand;additionalOffHand=!additionalOffHand;}
        lastSwing=tickCount;entityData.set(LEFT,off==(getMainArm()==HumanoidArm.RIGHT));if(isArmsOnlyMode()&&off)entityData.set(SUMMON_MODE,15);
    }
    @Override public boolean beginAttack(boolean heavy){return start(heavy?finisher>=.5F&&ProgressionService.unlocked(owner(),1)?Task.KICK:Task.HEAVY:Task.LIGHT);}
    @Override public boolean beginHeld(boolean guard){return start(guard?Task.BLOCK:Task.FLAME_BURST);}
    @Override public boolean beginSpecial(int phase){return phase>0&&phase<Task.values().length&&start(Task.values()[phase]);}
    @Override public boolean beginUppercut(){return start(Task.KICK);}
    @Override public void refreshHeld(){if(held(task()))heldLease=12;}
    @Override public void stopHeld(){releaseHeld(false,elapsed);}
    public void releaseHeld(boolean cancelled,int ticks){
        if(charged(task())&&actionPhase()==4){if(!cancelled&&ticks>=20){phase(2,1);firstPerform=true;}else idle();}
        else if(task()==Task.RED_BIND&&finisher>=.5F&&landedBind()!=null){retainedBind=true;}
        else if(held(task()))recover();
    }
    private void phase(int value,int ticks){entityData.set(PHASE,value);duration=Math.max(1,ticks);elapsed=0;sync();}
    private void sync(){entityData.set(TICKS,elapsed);entityData.set(DURATION,duration);entityData.set(FINISHER,finisher);var bind=landedBind();entityData.set(BOUND,bind==null?-1:bind.attached().getId());}
    private void idle(){
        entityData.set(TASK,0);entityData.set(PHASE,0);duration=elapsed=0;previousLight=false;retainedBind=false;
        if(queued!=null){var next=queued;queued=null;start(next);return;}
        if(isArmsOnlyMode()){int ticks=unsummonDuration();entityData.set(FADE,ticks);entityData.set(UNSUMMON,0);playOriginal("magicians_red_unsummon");}sync();
    }
    private int unsummonDuration(){return owner()!=null&&StandAttachments.resolve(owner()).isBurst()?3:(int)Math.ceil(7/((staminaCondition()*2+1)/3));}
    private void recover(){if(recovery>0)phase(3,recovery);else idle();}
    public MRRedBindEntity landedBind(){return level().getEntitiesOfClass(MRRedBindEntity.class,getBoundingBox().inflate(16),b->b.getOwner()==this&&b.attached()!=null).stream().findFirst().orElse(null);}
    @Override public float userWalkSpeed(){return manuallyControlled()||actionPhase()==0||actionPhase()==3?1:task()==Task.BLOCK||task()==Task.RED_BIND?.3F:task()==Task.LIGHT?1:.5F;}
    @Override public void tick(){
        if(TimeStopService.isFrozen(this))return;super.tick();if(!(level() instanceof ServerLevel server))return;
        if(!live()){discard();return;}entityData.set(AGE,tickCount);if(tickCount>20)entityData.set(SUMMON_ANIMATION,false);updateAttributes();
        if(manuallyControlled()&&(--controlLease<=0||owner().position().distanceToSqr(anchor)>4||owner().isPassenger()))stopControl();positionFromOwner();
        int unsummon=entityData.get(UNSUMMON);if(unsummon>=0){if(unsummon>=unsummonDuration())discard();else entityData.set(UNSUMMON,unsummon+1);return;}
        if(entityData.get(SUMMON_LOCK)>0){entityData.set(SUMMON_LOCK,entityData.get(SUMMON_LOCK)-1);return;}
        if(entityData.get(WEAKNESS)>0&&!VampireCombatService.isStunned(this))entityData.set(WEAKNESS,entityData.get(WEAKNESS)-1);
        if(VampireCombatService.isStunned(this)||VampireCombatService.isStunned(owner())){queued=null;idle();return;}
        heavyHitCooldowns.replaceAll((id,ticks)->ticks-1);heavyHitCooldowns.values().removeIf(ticks->ticks<=0);
        int phase=actionPhase();
        if(phase==1){
            if(task()==Task.KICK){int left=duration-elapsed;if(left==2){var target=targetPosition();var vector=target==null?getLookAngle().scale(3):target.subtract(getEyePosition());if(target!=null)vector=vector.normalize().scale(Math.clamp(vector.length()-getBbWidth(),0,3));setPos(position().add(vector));}else if(left==1){setDeltaMovement(Vec3.ZERO);var bind=landedBind();if(bind!=null&&bind.kick())bind.discard();}}
            else if(heavyAttack()&&duration-elapsed==3)playOriginal("stand_punch_heavy_swing");
            if(++elapsed>=duration){phase(2,task()==Task.LIGHT?StandStatFormulas.getLightAttackRecovery(attackSpeed(),finisher):1);firstPerform=true;}
        }else if(phase==2){
            if(held(task())&&!retainedBind&&--heldLease<0){releaseHeld(true,elapsed);return;}
            perform(server);firstPerform=false;if(actionPhase()==2&&++elapsed>=duration)recover();
        }else if(phase==4){if(--heldLease<0)idle();else elapsed=Math.min(duration,elapsed+1);}
        else if(phase==5){if(--heldLease<0)idle();else elapsed++;}
        else if(phase==3||phase==6){if(++elapsed>=duration)idle();}
        if(task()!=Task.RED_BIND&&!heavyAttack()){if(decay>0)decay--;else finisher=Math.max(0,finisher-(finisher<.5F?.0125F:.025F));}sync();
    }
    private Vec3 targetPosition(){return attackTarget!=null&&attackTarget.isAlive()?attackTarget.getEyePosition():aimPoint;}
    private boolean spend(float amount){if(StandConfig.staminaFree(owner()))return true;var state=owner().getAttachedOrCreate(StandAttachments.PROGRESS);owner().setAttached(StandAttachments.PROGRESS,state.withStamina(Math.max(0,state.stamina()-amount)));return state.stamina()>=amount||StandAttachments.resolve(owner()).isBurst();}
    private void perform(ServerLevel server){
        if(firstPerform)switch(task()){
            case LIGHT->{addFinisher(.1F);punch(false);spend(10);}
            case HEAVY,KICK->{punch(true);spend(50);}
            case FIREBALL->{var shot=new MRFireballEntity(this);shot.shootFromStand(getXRot(),getYRot(),2,projectileInaccuracy(2));server.addFreshEntity(shot);StandPerception.playSound(this,net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,1,1);spend(75);}
            case CROSSFIRE,CROSSFIRE_SPECIAL->{crossfire(server);spend(500);}
            case RED_BIND->{server.addFreshEntity(new MRRedBindEntity(this));playOriginal("magicians_red_fire_ability");}
            default->{}
        }
        if(task()==Task.FLAME_BURST){var flame=new MRFlameEntity(this);float speed=ProgressionService.state(owner()).level()>=3?1:.5F;flame.shootFromStand(getXRot()+(random.nextFloat()-.5F)*10,getYRot()+(random.nextFloat()-.5F)*10,speed,0);server.addFreshEntity(flame);if(!spend(3))stopHeld();}
        else if(task()==Task.RED_BIND&&!spend(1))recover();
    }
    private void crossfire(ServerLevel server){
        boolean small=task()==Task.CROSSFIRE_SPECIAL;var target=targetPosition();if(small&&target==null){var hit=aim(64);if(hit.getType()!=HitResult.Type.MISS)target=hit.getLocation();}
        int count=small?8:1;for(int i=0;i<count;i++){
            MRCrossfireEntity ankh=small?new MRCrossfireSpecialEntity(this):new MRCrossfireEntity(this);if(small&&target!=null)ankh.setTarget(target);
            double pitch=0,yaw=0;if(i>0){double a=(i/(double)count+.5)*Math.PI,x=Math.cos(a)*Math.cos(a),y=Math.sin(a)*Math.sin(a),z=2.25;
                yaw=Math.acos(Math.sqrt((x+z)/(x+y+z)))*(a>Math.PI?-1:1);pitch=Math.acos(Math.sqrt((y+z)/(x+y+z)))*(a>Math.PI/2&&a<Math.PI*1.5?-1:1);}
            ankh.shootFromStand(getXRot()+(float)Math.toDegrees(pitch),getYRot()+(float)Math.toDegrees(yaw),small?2:1.25F,0);ankh.setScale((float)efficiency());server.addFreshEntity(ankh);
        }playOriginal("magicians_red_fire_ability");
    }
    public boolean canHarm(Entity target){return target!=this&&target!=user()&&target.isAlive()&&!target.isSpectator()&&target.isPickable()&&!(target instanceof StandManifestation stand&&stand.ownerId()==ownerId())&&(!(target instanceof Player player)||owner()==null||owner().canHarmPlayer(player))&&(!(target instanceof LivingEntity living)||canAttack(living));}
    public HitResult aim(double range){
        LivingEntity aiming=manuallyControlled()?this:user();if(aiming==null)aiming=this;Vec3 from=aiming.getEyePosition(),to=from.add(aiming.getLookAngle().scale(range));
        HitResult result=level().clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,aiming));double closest=result.getType()==HitResult.Type.MISS?range*range:from.distanceToSqr(result.getLocation());
        for(var entity:level().getEntities(this,new AABB(from,to).inflate(1),this::canHarm)){var box=entity.getBoundingBox().inflate(entity.getPickRadius());var point=box.contains(from)?Optional.of(from):box.clip(from,to);if(point.isPresent()&&from.distanceToSqr(point.get())<closest){closest=from.distanceToSqr(point.get());result=new EntityHitResult(entity,point.get());}}
        return result;
    }
    private void punch(boolean heavy){
        var result=aim(2.5);Entity target=attackTarget!=null&&attackTarget.isAlive()?attackTarget:result instanceof EntityHitResult hit?hit.getEntity():null;
        if(target==null){if(result instanceof BlockHitResult block&&result.getType()==HitResult.Type.BLOCK)breakBlock(block,heavy);playOriginal(heavy?task()==Task.KICK?"magicians_red_kick_heavy":"magicians_red_punch_heavy":"magicians_red_punch_light");return;}
        if(!canHarm(target)||target.distanceToSqr(this)>Math.pow(2.5+target.getBbWidth(),2)||heavy&&heavyHitCooldowns.containsKey(target.getUUID()))return;
        float amount=(heavy?StandStatFormulas.getHeavyAttackDamage(attackDamage()):StandStatFormulas.getLightAttackDamage(attackDamage()))*StandConfig.server().standPowerMultiplier();
        int fire=target.getRemainingFireTicks();MRProjectileEntity.ignite(target,10);var source=damageSources().mobAttack(this);var server=(ServerLevel)level();
        boolean hurt=target instanceof LivingEntity living?StandDamage.hurt(server,living,source,amount):target.hurtServer(server,source,amount);if(!hurt)target.setRemainingFireTicks(fire);
        if(!heavy)addFinisher(.1F);else heavyHitCooldowns.put(target.getUUID(),10);
        if(hurt&&target instanceof LivingEntity living){
            double strength=heavy?(.5+attackDamage()/(8-heavyFinisher*4))*(task()==Task.KICK?1.2:1):Math.min(guardDamage/5,1)*.5;
            if(strength>0){var vector=target.position().subtract(position());living.knockback(strength, -vector.x,-vector.z,source,amount);living.knockback(strength,-vector.x,-vector.z,source,amount);living.syncVelocity=true;}
            if(heavy)com.github.standobyte.jojo.fabric.stand.StandHeavyImpact.start(this,living,attackDamage(),precision());
            playOriginal(heavy?task()==Task.KICK?"magicians_red_kick_heavy":"magicians_red_punch_heavy":"magicians_red_punch_light");
        }
    }
    private void addFinisher(float amount){finisher=Math.min(1,finisher+amount);decay=40;}
    private void breakBlock(BlockHitResult hit,boolean heavy){
        if(heavy){com.github.standobyte.jojo.fabric.stand.StandHeavyImpact.punchBlock(this,hit,attackDamage(),precision());return;}
        var server=(ServerLevel)level();var pos=hit.getBlockPos();var state=server.getBlockState(pos);
        float hardness=StandStatFormulas.getStandBreakBlockHardness(state.getDestroySpeed(server,pos),state.requiresCorrectToolForDrops());
        int harvest=state.is(BlockTags.NEEDS_DIAMOND_TOOL)?3:state.is(BlockTags.NEEDS_IRON_TOOL)?2:state.is(BlockTags.NEEDS_STONE_TOOL)?1:0;
        if(server.getGameRules().get(JojoGameRules.BREAK_BLOCKS)&&StandStatFormulas.isBlockBreakable(attackDamage(),hardness,harvest)&&MRProjectileEntity.permitted(server,pos,this)){
            try(var capture=RestorationHooks.begin(server,pos,state,server.getBlockEntity(pos))){capture.commit(server.destroyBlock(pos,!owner().isCreative(),this));}
        }
    }
    private boolean blockable(DamageSource source){return source.getDirectEntity()!=null&&!source.is(DamageTypeTags.BYPASSES_ARMOR)&&!VampireCombatService.isStunned(this);}
    @Override public float protectOwner(DamageSource source,float damage){
        var player=owner();var pos=source.getSourcePosition();if(player==null||damage<=0||task()!=Task.BLOCK||actionPhase()!=5||!blockable(source)||pos==null||pos.subtract(transferringDamage?position():player.position()).normalize().dot(getLookAngle())<=.5)return damage;
        var progress=player.getAttachedOrCreate(StandAttachments.PROGRESS);float cost=StandStatFormulas.getBlockStaminaCost(damage),blocked=StandConfig.staminaFree(player)||cost==0?1:Math.min(progress.stamina()/cost,1);spend(cost);playOriginal("stand_damage_block");
        if(blocked<1)phase(6,StandStatFormulas.getBlockingBreakTicks(durability()));blocked+=(1-blocked)*.5F;
        float remaining=damage*(1-StandStatFormulas.getPhysicalResistance(durability(),attackDamage(),blocked,damage,StandConfig.server().standResistanceMultiplier()));guardDamage+=remaining;return remaining;
    }
    @Override public boolean hurtServer(ServerLevel server,DamageSource source,float amount){return StandDamage.canDamageBody(this,source)&&super.hurtServer(server,source,amount);}
    @Override protected void actuallyHurt(ServerLevel server,DamageSource source,float damage){
        var player=owner();if(player==null||!player.isAlive())return;var pos=source.getSourcePosition();
        if(task()==Task.NONE&&!manuallyControlled()&&blockable(source)&&pos!=null&&pos.subtract(position()).normalize().dot(getLookAngle())>.5&&start(Task.BLOCK))heldLease=4;
        damage=getDamageAfterMagicAbsorb(source,getDamageAfterArmorAbsorb(source,damage));var resistance=player.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
        if(resistance!=null&&!source.is(DamageTypeTags.BYPASSES_EFFECTS)&&!source.is(DamageTypeTags.BYPASSES_RESISTANCE))damage=Math.max(0,damage*(25-(resistance.getAmplifier()+1)*5)/25);
        transferringDamage=true;try{
            if(blockable(source)){boolean blocking=task()==Task.BLOCK&&actionPhase()==5&&pos!=null&&pos.subtract(position()).normalize().dot(getLookAngle())>.5;
                damage=blocking?protectOwner(source,damage):damage*(1-StandStatFormulas.getPhysicalResistance(durability(),attackDamage(),task()==Task.NONE?0:.5F,damage,StandConfig.server().standResistanceMultiplier()));}
            float remaining=Math.max(0,damage-getAbsorptionAmount());setAbsorptionAmount(Math.max(0,getAbsorptionAmount()-damage));if(remaining>0)StandDamage.hurt(server,player,new LinkedDamage(source),remaining);
        }finally{transferringDamage=false;}
    }
    private static final class LinkedDamage extends DamageSource {
        private final DamageSource original;
        LinkedDamage(DamageSource source){super(source.typeHolder(),source.getDirectEntity(),source.getEntity());original=source;}
        @Override public boolean is(TagKey<DamageType> tag){return tag.equals(DamageTypeTags.BYPASSES_ARMOR)||tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS)||tag.equals(DamageTypeTags.BYPASSES_EFFECTS)||tag.equals(DamageTypeTags.BYPASSES_RESISTANCE);}
        @Override public Vec3 getSourcePosition(){return null;}
        @Override public float getFoodExhaustion(){return .1F;}
        @Override public boolean scalesWithDifficulty(){return false;}
        @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return original.getLocalizedDeathMessage(victim);}
    }
    @Override public boolean manuallyControlled(){return entityData.get(CONTROL);}
    @Override public Vec3 bodyAnchor(){return anchor;}
    @Override public void translateControlAnchor(Vec3 value){if(anchor!=null&&manuallyControlled())anchor=anchor.add(value);}
    @Override public boolean startControl(){if(!live()||owner().isPassenger()||!StandParts.has(owner(),StandParts.Part.MAIN_BODY))return false;anchor=owner().position();controlYaw=owner().getYRot();controlPitch=owner().getXRot();controlLease=12;entityData.set(CONTROL,true);return true;}
    @Override public void stopControl(){entityData.set(CONTROL,false);anchor=null;input=Vec3.ZERO;}
    @Override public void control(float sideways,float forward,float vertical,float yaw,float pitch){
        if(!manuallyControlled()||!live()||!Float.isFinite(sideways)||!Float.isFinite(forward)||!Float.isFinite(vertical)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        controlYaw=yaw;controlPitch=Math.clamp(pitch,-90,90);var look=Vec3.directionFromRotation(0,yaw);var side=new Vec3(-look.z,0,look.x);
        input=look.scale(Math.clamp(forward,-1,1)).add(side.scale(Math.clamp(sideways,-1,1))).add(0,Math.clamp(vertical,-1,1),0);if(input.lengthSqr()>1)input=input.normalize();controlLease=12;
    }
    private void positionFromOwner(){
        var player=owner();if(manuallyControlled()){
            player.setPos(anchor);player.setDeltaMovement(Vec3.ZERO);var desired=position().add(input.scale(getAttributeValue(Attributes.MOVEMENT_SPEED)));var delta=desired.subtract(anchor);
            if(delta.lengthSqr()>100)desired=anchor.add(delta.normalize().scale(10));if(level().hasChunkAt(BlockPos.containing(desired)))setPos(desired);setRot(controlYaw,controlPitch);
        }else if(!(task()==Task.KICK&&(actionPhase()==1&&duration-elapsed<=2||actionPhase()==2||actionPhase()==3))){
            var forward=Vec3.directionFromRotation(0,player.getYRot());var side=new Vec3(-forward.z,0,forward.x);
            var offset=isArmsOnlyMode()?new Vec3(0,.15,0):actionPhase()==0?forward.scale(-.6).add(side.scale(.8)):forward.scale(task()==Task.BLOCK?.3:1);
            if(heavyAttack()&&!isArmsOnlyMode()){
                var target=targetPosition();double length=2;
                if(target!=null){var delta=target.subtract(player.position());length=Math.clamp(Math.sqrt(delta.x*delta.x+delta.z*delta.z)-1-(attackTarget==null?.5:attackTarget.getBbWidth()/2),.5,2);}
                offset=player.getLookAngle().scale(length);
            }
            setPos(player.position().add(offset));setRot(player.getYRot(),player.getXRot());
        }setYBodyRot(getYRot());setYHeadRot(getYRot());setDeltaMovement(Vec3.ZERO);
    }
    @Override public boolean isInvisible(){return true;}
    @Override public boolean isInvisibleTo(Player player){return !StandPerception.canSeeStand(player)||!StandPerception.trueSpectator(player)&&super.isInvisible();}
    @Override public void playOriginal(String sound){StandPerception.playSound(this,sound,1,1);}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canCollideWith(Entity entity){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public net.minecraft.world.scores.PlayerTeam getTeam(){return owner()==null?super.getTeam():owner().getTeam();}
}
