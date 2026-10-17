package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import com.github.standobyte.jojo.core.HamonSkills;
import com.github.standobyte.jojo.fabric.mixin.HamonMiningAccess;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server authority for original Hamon training, breathing, teachers, skills and lifecycle. */
public final class HamonService {
    private static final Map<LivingEntity,Runtime> RUNTIME=new WeakHashMap<>();
    private static final Identifier DAMAGE=RipplesFabric.id("hamon_breathing_damage"),ATTACK_SPEED=RipplesFabric.id("hamon_breathing_attack_speed"),
            MOVE_SPEED=RipplesFabric.id("hamon_breathing_movement_speed"),RUNNING=RipplesFabric.id("hamon_running_complete"),MINING=RipplesFabric.id("hamon_mining_complete");
    private static final class Runtime {
        boolean meditating,breathing,suffocating; int meditationTicks,recoveryMeditationTicks,miningDelay,attributeSignature=Integer.MIN_VALUE;
        long day=-1; Vec3 previousPosition;
        final Set<UUID> learners=new LinkedHashSet<>();
        TeacherInfo teachers=new TeacherInfo(false,List.of());
    }
    public record TeacherInfo(boolean nearby,List<String> skills) { public TeacherInfo {skills=List.copyOf(skills);} }
    private static Runtime runtime(LivingEntity entity) {return RUNTIME.computeIfAbsent(entity,e->new Runtime());}
    public static HamonProgress state(LivingEntity entity) {
        var state=entity.getAttached(HamonAttachments.PROGRESS);return state==null?HamonProgress.EMPTY:state;
    }
    public static HamonHudState hud(LivingEntity entity) {
        var state=entity.getAttached(HamonAttachments.HUD);return state==null?HamonHudState.EMPTY:state;
    }
    public static boolean hasHamon(LivingEntity entity) {return state(entity).hamon();}
    public static boolean isMeditating(LivingEntity entity) {return hud(entity).meditating();}
    public static int meditationTicks(LivingEntity entity) {return hud(entity).meditationTicks();}
    public static boolean isBreathingHeld(LivingEntity entity) {return hud(entity).breathing();}
    public static boolean wearingMask(LivingEntity entity) {return entity.getItemBySlot(EquipmentSlot.HEAD).is(HamonItems.BREATH_CONTROL_MASK);}
    private static boolean creative(LivingEntity entity) {return entity instanceof Player p && p.isCreative();}
    public static boolean canAct(LivingEntity entity) {return entity.isAlive() && !entity.isSpectator() && !TimeStopService.isFrozen(entity);}
    public static void initialize() {
        HamonAttachments.initialize();HamonConfig.initialize();HamonPayload.initialize();
        var phase=RipplesFabric.id("hamon_after_attachment_copy");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE,phase);
        ServerPlayerEvents.AFTER_RESPAWN.register(phase,(oldPlayer,newPlayer,alive)->{
            var old=state(oldPlayer);RUNTIME.remove(oldPlayer);RUNTIME.remove(newPlayer);
            newPlayer.setAttached(HamonAttachments.PROGRESS,alive || HamonConfig.server().keepHamonOnDeath()?old.refreshExerciseBonuses():HamonProgress.EMPTY);
            newPlayer.setAttached(HamonAttachments.HUD,HamonHudState.EMPTY);
            updateAttributes(newPlayer,runtime(newPlayer));syncHud(newPlayer,runtime(newPlayer));
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            RUNTIME.remove(handler.player);
            if(hasHamon(handler.player)){setState(handler.player,state(handler.player).refreshExerciseBonuses());updateAttributes(handler.player,runtime(handler.player));refreshTeachers(handler.player);HamonAdvancements.onStats(handler.player);}
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            RUNTIME.remove(handler.player);
            for(var runtime:RUNTIME.values())runtime.learners.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->RUNTIME.clear());
    }
    private static void setState(LivingEntity entity,HamonProgress next) {
        if (entity.level().isClientSide())return;
        var old=state(entity);
        if (!old.equals(next)) {
            entity.setAttached(HamonAttachments.PROGRESS,next);
            if(old.strengthPoints()!=next.strengthPoints() || old.controlPoints()!=next.controlPoints() || old.breathingLevel()!=next.breathingLevel()) {
                updateAttributes(entity,runtime(entity));
                if(entity instanceof ServerPlayer player)HamonAdvancements.onStats(player);
            }
        }
    }
    public static boolean grant(LivingEntity entity) {
        if(entity.level().isClientSide() || SpeciesHooks.hasNonStand(entity))return false;
        setState(entity,HamonProgress.initial());
        if(creative(entity))maxStats(entity);
        updateAttributes(entity,runtime(entity));syncHud(entity,runtime(entity));
        if(entity instanceof ServerPlayer player)VampireAdvancements.onHamonAcquired(player);
        return true;
    }
    public static void grantMaster(LivingEntity entity) {
        if(!hasHamon(entity))grant(entity);
        if(!hasHamon(entity))return;
        maxStats(entity);
        var next=state(entity);
        for(var skill:HamonSkills.all())if(skill.base())next=next.learn(skill.id());
        setState(entity,next);
    }
    private static void maxStats(LivingEntity entity) {
        setState(entity,state(entity).withStats(HamonProgress.MAX_HAMON_POINTS,HamonProgress.MAX_HAMON_POINTS).withBreathing(100).withTutorial(false));
    }
    public static void clear(LivingEntity entity) {
        if(entity.level().isClientSide())return;
        HamonActionService.clear(entity);
        setState(entity,HamonProgress.EMPTY);RUNTIME.remove(entity);clearAttributes(entity);
        entity.setAttached(HamonAttachments.HUD,HamonHudState.EMPTY);entity.refreshDimensions();
        removeEffect(entity,"cheat_death");
    }
    public static void abandon(ServerPlayer player) {
        if(!canAct(player) || !hasHamon(player))return;
        clear(player);HamonAdvancements.onAbandon(player);
    }
    public static void setStats(LivingEntity entity,int strength,int control) {if(hasHamon(entity))setState(entity,state(entity).withStats(strength,control));}
    public static void setBreathing(LivingEntity entity,float level) {if(hasHamon(entity) && Float.isFinite(level))setState(entity,state(entity).withBreathing(level));}
    public static void energy(LivingEntity entity,float value) {if(hasHamon(entity) && Float.isFinite(value))setState(entity,state(entity).withEnergy(value));}
    public static void breathStability(LivingEntity entity,float value,int noRecoveryTicks) {
        if(hasHamon(entity) && Float.isFinite(value))setState(entity,state(entity).withBreathStability(value,Math.max(0,noRecoveryTicks)));
    }
    public static float bloodstreamEfficiency(LivingEntity entity) {
        var bleeding=entity.getEffect(VampireBlood.BLEEDING);var freeze=entity.getEffect(VampireCombatService.FREEZE);
        float bleed=bleeding==null?0:Math.min((bleeding.getAmplifier()+1)*.2F,.8F);
        float cold=freeze==null?0:Math.min((freeze.getAmplifier()+1)*.25F,1);
        for(var effect:entity.getActiveEffects()) {
            String id=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
            if(id.equals("mowziesmobs:frozen") || id.equals("vampirism:freeze"))cold=1;
            else if(id.equals("twilightforest:frosted"))cold=Math.max(cold,Math.min((effect.getAmplifier()+1)*.25F,1));
        }
        return (1-bleed)*(1-cold);
    }
    private static float actualCost(LivingEntity entity,float cost) {return entity.getItemBySlot(EquipmentSlot.HEAD).is(VampireHamon.SCARF)?cost*.6F:cost;}
    public static float efficiency(LivingEntity entity,float cost) {return efficiency(entity,cost,"");}
    public static float efficiency(LivingEntity entity,float cost,String skill) {
        if(!Float.isFinite(cost) || cost<0)return 0;
        return state(entity).actionEfficiency(actualCost(entity,cost),wearingMask(entity),skill,bloodstreamEfficiency(entity));
    }
    public static float consumeEnergy(LivingEntity entity,float cost) {return consumeEnergy(entity,cost,"");}
    public static float consumeEnergy(LivingEntity entity,float cost,String skill) {
        float efficiency=efficiency(entity,cost,skill);
        if(efficiency>0)consumeResourceEnergy(entity,cost);
        return efficiency;
    }
    /** Original INonStandPower.consumeEnergy: resource efficiency only, including stability fallback. */
    public static float consumeResourceEnergy(LivingEntity entity,float cost) {
        if(!hasHamon(entity) || !Float.isFinite(cost) || cost<0)return 0;
        float reduced=actualCost(entity,cost);boolean mask=wearingMask(entity);var before=state(entity);
        float efficiency=before.energyEfficiency(reduced,mask);
        if(!entity.level().isClientSide() && !creative(entity)) {
            setState(entity,before.consume(reduced,mask));
            if(before.energy()==0 && before.breathStability()*(mask?1:2.5F)<reduced)entity.setAirSupply(0);
        }
        return efficiency;
    }
    public static void gainActionPoints(LivingEntity entity,boolean strength,float energyUsed) {
        if(!hasHamon(entity) || entity.level().isClientSide())return;
        var config=HamonConfig.server();
        setState(entity,state(entity).gainPoints(strength?HamonSkills.Stat.STRENGTH:HamonSkills.Stat.CONTROL,energyUsed,config.hamonPointsMultiplier(),config.breathingHamonStatGap()));
    }
    public static void setBreathingHeld(LivingEntity entity,boolean held) {
        if(entity.level().isClientSide() || !hasHamon(entity))return;
        var rt=runtime(entity);rt.breathing=held;syncHud(entity,rt);
    }
    public static void setMeditating(LivingEntity entity,boolean enabled) {
        if(entity.level().isClientSide() || !hasHamon(entity) || enabled && (!canAct(entity) || !entity.onGround()))return;
        var rt=runtime(entity);
        if(rt.meditating!=enabled) {
            rt.meditating=enabled;rt.meditationTicks=0;rt.recoveryMeditationTicks=0;
            if(enabled){rt.breathing=false;entity.yBodyRot=entity.getYRot();}
            syncHud(entity,rt);entity.refreshDimensions();
        }
    }
    /** Called once per LivingEntity tick, so NPC power users have the same energy/stability lifecycle. */
    public static void tick(LivingEntity entity) {
        if(entity.level().isClientSide()) {
            var display=hud(entity);var previous=RUNTIME.get(entity);
            if(!hasHamon(entity) && previous==null)return;
            var local=runtime(entity);
            boolean changed=local.meditating!=display.meditating() || display.meditating() && display.meditationTicks()<=35 && local.meditationTicks!=display.meditationTicks();
            local.meditating=display.meditating();local.meditationTicks=display.meditationTicks();
            if(changed)entity.refreshDimensions();
            return;
        }
        if(!hasHamon(entity) || TimeStopService.isFrozen(entity))return;
        Runtime rt=runtime(entity);
        if(!entity.isAlive()) {
            if(rt.meditating || rt.breathing){rt.meditating=false;rt.breathing=false;rt.meditationTicks=0;rt.recoveryMeditationTicks=0;syncHud(entity,rt);entity.refreshDimensions();}
            return;
        }
        var current=state(entity);var config=HamonConfig.server();
        if(current.tcsa() && (creative(entity) || !current.technique().isEmpty()))current=current.withTutorial(false);
        int air=entity.getAirSupply();
        if(!rt.suffocating && air>0 && air<entity.getMaxAirSupply()-1) {
            int chance=(int)(current.breathingLevel()*current.breathStability()/current.maxBreathStability())-1;
            if(entity.tickCount%100<chance)entity.setAirSupply(++air);
        }
        rt.suffocating=false;
        if(air<=-19)current=current.withBreathStability(0,80);
        var next=current.tickBreath(rt.breathing,air>=entity.getMaxAirSupply(),wearingMask(entity),rt.meditating,
                rt.recoveryMeditationTicks,config.hamonEnergyTicksDown(),hasEffect(entity,"dying_body"),entity.getRandom().nextFloat());
        if(current.breathStability()>0 && next.breathStability()==0)entity.setAirSupply(0);
        setState(entity,next);
        if(entity instanceof ServerPlayer player) {
            // PowerBaseImpl.tick processes tickUser/tickExercises before checking the new day.
            tickExercises(player,rt);
            long day=entity.level().getOverworldClockTime()/24000;
            if(rt.day!=-1 && rt.day!=day)trainingDay(player);
            rt.day=day;
            if(entity.tickCount%10==0)rt.teachers=nearbyTeachers(player);
        }
        rt.learners.removeIf(id->{var learner=entity.level().getPlayerByUUID(id);return learner==null || !learner.isAlive() || learner.distanceToSqr(entity)>64;});
        updateAttributes(entity,rt);syncHud(entity,rt);
    }
    private static boolean hasMovementInput(ServerPlayer player) {
        var input=player.getLastClientInput();return input.forward() || input.backward() || input.left() || input.right() || input.jump();
    }
    private static void tickExercises(ServerPlayer player,Runtime rt) {
        var state=state(player);int before=state.completedExercises();var pos=player.position();
        // Preserve HamonData's literal x/y movement test, including its original omission of z.
        boolean changed=rt.previousPosition==null || rt.previousPosition.x!=pos.x || rt.previousPosition.y!=pos.y;rt.previousPosition=pos;
        var mining=(HamonMiningAccess)player.gameMode;
        boolean destroying=mining.jojo$hamonDestroying() || mining.jojo$hamonDelayedDestroy();
        if(destroying)rt.miningDelay=6;else destroying=rt.miningDelay-->0;
        if(destroying)state=state.exercise(HamonProgress.Exercise.MINING,1);
        if(changed && player.isSwimming() && hasMovementInput(player))state=state.exercise(HamonProgress.Exercise.SWIMMING,1);
        else if(changed && player.isSprinting() && player.onGround() && !player.isSwimming())state=state.exercise(HamonProgress.Exercise.RUNNING,1);
        if(rt.meditating) {
            if(++rt.meditationTicks>=40) {state=state.exercise(HamonProgress.Exercise.MEDITATION,1);rt.recoveryMeditationTicks++;}
            if(rt.meditationTicks<=35) {syncHud(player,rt);player.refreshDimensions();}
            player.getFoodData().addExhaustion(-.0025F);
            if(player.tickCount%200==0 && player.getHealth()<player.getMaxHealth() && player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION))player.heal(1);
        }
        setState(player,state);
        int complete=state.completedExercises();
        if(complete>before && state.breathingLevel()<100) {
            player.sendOverlayMessage(Component.translatable("hamon.exercise.all.count.message"+(complete>=4?".4":""),complete,4));
            if(complete==3)player.sendSystemMessage(Component.translatable("hamon.exercise.all.count.message2.3",new java.text.DecimalFormat("#.##").format(state.dayIncrease(wearingMask(player),HamonConfig.server().breathingTrainingDeterioration(),HamonConfig.server().breathingTrainingMultiplier()))));
            else if(complete==4)player.sendSystemMessage(Component.translatable("hamon.exercise.all.count.message2.4",2));
        }
    }
    public static void trainingDay(ServerPlayer player) {
        var config=HamonConfig.server();setState(player,state(player).nextDay(wearingMask(player),config.breathingTrainingDeterioration(),config.breathingTrainingMultiplier()));
        if(state(player).hasSkill("cheat_death"))refreshCheatDeath(player);
        updateAttributes(player,runtime(player));
    }
    public static void suffocateTick(LivingEntity entity,float speed) {
        if(!hasHamon(entity) || entity.level().isClientSide())return;
        var s=state(entity);breathStability(entity,Math.max(s.breathStability()-s.maxBreathStability()*speed,1),80);runtime(entity).suffocating=true;
    }
    public static TeacherInfo nearbyTeachers(LivingEntity learner) {
        var teachers=learner.level().getEntitiesOfClass(LivingEntity.class,learner.getBoundingBox().inflate(3),e->e!=learner && e.isAlive() && hasHamon(e));
        var skills=new TreeSet<String>();for(var teacher:teachers)for(var id:state(teacher).learnedSkills())if(HamonSkills.skill(id).requiresTeacher())skills.add(id);
        return new TeacherInfo(!teachers.isEmpty(),List.copyOf(skills));
    }
    public static void refreshTeachers(ServerPlayer player) {var rt=runtime(player);rt.teachers=nearbyTeachers(player);syncHud(player,rt);}
    public static boolean learnSkill(ServerPlayer player,String id) {
        if(!canAct(player) || !hasHamon(player))return false;
        var teacher=nearbyTeachers(player);var config=HamonConfig.server();
        String problem=HamonSkills.learningProblem(state(player),id,teacher.skills(),teacher.nearby(),config.mixHamonTechniques(),config.techniqueSkillsRequirement());
        if(!problem.isEmpty()){player.sendOverlayMessage(Component.translatable(problem));refreshTeachers(player);return false;}
        return addSkill(player,id);
    }
    /** Administrative/master integration bypasses prerequisites, while retaining original one-time skill rewards. */
    public static boolean addSkill(LivingEntity entity,String id) {
        var skill=HamonSkills.skill(id);if(entity.level().isClientSide() || !hasHamon(entity) || skill==null || state(entity).hasSkill(id))return false;
        setState(entity,state(entity).learn(skill.id()));
        if(skill.id().equals("cheat_death"))refreshCheatDeath(entity);
        if(entity instanceof ServerPlayer player) {
            if(skill.id().equals("satiporoja_scarf") && !player.getAttachedOrCreate(HamonAttachments.SCARF_GIVEN)) {
                player.setAttached(HamonAttachments.SCARF_GIVEN,true);var scarf=new ItemStack(VampireHamon.SCARF);
                HamonItems.give(player,scarf);
            }
            HamonAdvancements.onLearn(player,skill.id());
        }
        return true;
    }
    public static boolean pickTechnique(ServerPlayer player,String id) {
        String key=HamonSkills.normalize(id);var technique=HamonSkills.technique(key);
        if(!canAct(player) || !hasHamon(player) || !state(player).technique().isEmpty() || technique==null || HamonConfig.server().techniqueSkillsRequirement().isEmpty())return false;
        setState(player,state(player).withTechnique(key));HamonAdvancements.onTechnique(player,key);return true;
    }
    public static boolean resetSkills(ServerPlayer player,String tab) {
        if(!player.isCreative() || !hasHamon(player) || !(tab.equalsIgnoreCase("strength") || tab.equalsIgnoreCase("control") || tab.equalsIgnoreCase("technique")))return false;
        var next=state(player).resetSkills(tab);setState(player,next);if(!next.hasSkill("cheat_death"))removeEffect(player,"cheat_death");return true;
    }
    private static boolean nearbyInteraction(ServerPlayer player,LivingEntity target) {
        return target!=player && target.level()==player.level() && target.isAlive() && !target.isSpectator() && player.distanceToSqr(target)<=64 && player.hasLineOfSight(target);
    }
    public static boolean interactTeacher(ServerPlayer player,LivingEntity teacher) {
        if(!canAct(player) || !nearbyInteraction(player,teacher) || !hasHamon(teacher))return false;
        if(hasHamon(player)) {if(player.isCreative())maxStats(player);refreshTeachers(player);return true;}
        if(teacher instanceof Player) {
            runtime(teacher).learners.add(player.getUUID());syncHud(teacher,runtime(teacher));return true;
        }
        return startLearning(player,teacher);
    }
    public static boolean teach(ServerPlayer teacher,ServerPlayer learner) {
        if(!canAct(teacher) || !hasHamon(teacher) || !nearbyInteraction(teacher,learner) || !runtime(teacher).learners.remove(learner.getUUID()))return false;
        syncHud(teacher,runtime(teacher));return startLearning(learner,teacher);
    }
    private static boolean startLearning(ServerPlayer player,LivingEntity teacher) {
        if(SpeciesHooks.hasNonStand(player)) {player.sendOverlayMessage(Component.translatable("jojo.chat.message.cant_learn_hamon"));return false;}
        if(state(teacher).technique().equals("zeppeli")) {
            teacher.level().playSound(null,teacher.getX(),teacher.getY(),teacher.getZ(),JojoSounds.get("zeppeli_force_breath"),teacher.getSoundSource(),1,1);
            teacher.swing(InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
            boolean fail=player.getRandom().nextFloat()<=.01F;
            player.hurtServer(player.level(),player.damageSources().inWall(),Math.max(0,Math.min(fail?10:.1F,player.getHealth()-.0001F)));
            if(fail){player.setAirSupply(0);return false;}
        }
        if(!grant(player))return false;
        player.sendSystemMessage(Component.translatable("jojo.chat.message.learnt_hamon"));
        if(!player.getAttachedOrCreate(HamonAttachments.LEARNING_NOTIFIED)) {
            player.setAttached(HamonAttachments.LEARNING_NOTIFIED,true);
            player.sendSystemMessage(Component.translatable("jojo.chat.message.hamon_window_hint",Component.keybind("jojo.key.hamon_skills_window")));
        }
        return true;
    }
    public static void readIntro(ServerPlayer player) {player.setAttached(HamonAttachments.INTRO_READ,true);syncHud(player,runtime(player));}
    private static void syncHud(LivingEntity entity,Runtime rt) {
        var learners=new ArrayList<Integer>();for(var id:rt.learners){var player=entity.level().getPlayerByUUID(id);if(player!=null)learners.add(player.getId());}
        var value=new HamonHudState(rt.meditating,rt.meditationTicks,rt.breathing,rt.teachers.nearby(),rt.teachers.skills(),learners,entity.getAttachedOrCreate(HamonAttachments.INTRO_READ));
        if(!value.equals(hud(entity)))entity.setAttached(HamonAttachments.HUD,value);
    }
    private static void updateAttributes(LivingEntity entity,Runtime rt) {
        var state=state(entity);int level=state.hamon()?(int)state.breathingLevel():0;
        boolean running=state.hamon() && state.exerciseBonusActive(HamonProgress.Exercise.RUNNING),mining=state.hamon() && state.exerciseBonusActive(HamonProgress.Exercise.MINING);
        int signature=level*4+(running?2:0)+(mining?1:0);if(rt.attributeSignature==signature)return;rt.attributeSignature=signature;
        modifier(entity,Attributes.ATTACK_DAMAGE,DAMAGE,level*.03,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.ATTACK_SPEED,ATTACK_SPEED,level*.015,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.MOVEMENT_SPEED,MOVE_SPEED,level*.0005,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.MOVEMENT_SPEED,RUNNING,running?.1:0,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(entity,Attributes.ATTACK_SPEED,MINING,mining?.05:0,AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }
    private static void clearAttributes(LivingEntity entity) {
        modifier(entity,Attributes.ATTACK_DAMAGE,DAMAGE,0,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.ATTACK_SPEED,ATTACK_SPEED,0,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.MOVEMENT_SPEED,MOVE_SPEED,0,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.MOVEMENT_SPEED,RUNNING,0,AttributeModifier.Operation.ADD_VALUE);
        modifier(entity,Attributes.ATTACK_SPEED,MINING,0,AttributeModifier.Operation.ADD_VALUE);
    }
    private static void modifier(LivingEntity entity,Holder<Attribute> attribute,Identifier id,double value,AttributeModifier.Operation operation) {
        var instance=entity.getAttribute(attribute);if(instance==null)return;instance.removeModifier(id);if(value!=0)instance.addTransientModifier(new AttributeModifier(id,value,operation));
    }
    private static boolean hasEffect(LivingEntity entity,String id) {
        return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(RipplesFabric.id(id)).map(entity::hasEffect).orElse(false);
    }
    private static void removeEffect(LivingEntity entity,String id) {net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(RipplesFabric.id(id)).ifPresent(entity::removeEffect);}
    private static void refreshCheatDeath(LivingEntity entity) {
        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(RipplesFabric.id("cheat_death")).ifPresent(effect->entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,120000,0,false,false,true)));
    }
    public static EntityDimensions meditationDimensions(LivingEntity entity,EntityDimensions original) {
        if(!isMeditating(entity))return original;
        float scale=1-Math.min(meditationTicks(entity),35)*.0085F;
        return original.scale(1,scale).withEyeHeight(1.62F-(original.height()-original.height()*scale));
    }
    /** Forge SWIM_SPEED is multiplicative on water acceleration, not vanilla depth-strider interpolation. */
    public static float swimmingAcceleration(LivingEntity entity,float acceleration) {return hasHamon(entity)?acceleration*(1+(int)state(entity).breathingLevel()*.01F):acceleration;}
    private HamonService() { }
}
