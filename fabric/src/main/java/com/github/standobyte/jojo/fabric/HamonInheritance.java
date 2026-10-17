package com.github.standobyte.jojo.fabric;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
/** HamonUtil.hamonPerksOnDeath and the registered but normally inaccessible Cheat Death effect. */
public final class HamonInheritance {
    public static final Holder<MobEffect> CHEAT_DEATH=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("cheat_death"),new MobEffect(MobEffectCategory.BENEFICIAL,0xEADB84){});
    public static void initialize(){
        ServerLivingEntityEvents.ALLOW_DEATH.register((dead,source,amount)->{
            if(!dead.hasEffect(CHEAT_DEATH))return true;
            dead.setHealth(dead.getMaxHealth()/2);dead.removeEffect(CHEAT_DEATH);dead.clearFire();
            if(dead.level() instanceof ServerLevel level){
                level.sendParticles(ParticleTypes.POOF,dead.getX(),dead.getY(),dead.getZ(),20,dead.getBbWidth()*2-1,dead.getBbHeight(),dead.getBbWidth()*2-1,.02);
                dead.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,200,0,false,false,true));
                double x=dead.getX(),y=dead.getY(),z=dead.getZ();
                for(int i=0;i<16;i++){
                    double px=x+(dead.getRandom().nextDouble()-.5)*16,py=Math.clamp(y+dead.getRandom().nextInt(16)-8,level.getMinY(),level.getMaxY()),pz=z+(dead.getRandom().nextDouble()-.5)*16;
                    dead.stopRiding();if(dead.randomTeleport(px,py,pz,false,net.minecraft.tags.BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO))break;
                }
                for(Mob mob:level.getEntitiesOfClass(Mob.class,dead.getBoundingBox().inflate(8),m->m.getTarget()==dead)){mob.setTarget(null);mob.setLastHurtByMob(null);mob.getNavigation().stop();}
                if(dead instanceof ServerPlayer player&&HamonService.state(dead).technique().equals("joseph")){
                    if(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SHOW_DEATH_MESSAGES)) {
                        var players=level.getServer().getPlayerList();var team=player.getTeam();var message=source.getLocalizedDeathMessage(dead);
                        if(team==null||team.getDeathMessageVisibility()==net.minecraft.world.scores.Team.Visibility.ALWAYS)players.broadcastSystemMessage(message,false);
                        else if(team.getDeathMessageVisibility()==net.minecraft.world.scores.Team.Visibility.HIDE_FOR_OTHER_TEAMS)players.broadcastSystemToTeam(player,message);
                        else if(team.getDeathMessageVisibility()==net.minecraft.world.scores.Team.Visibility.HIDE_FOR_OWN_TEAM)players.broadcastSystemToAllExceptTeam(player,message);
                    }
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(Holder.direct(JojoSounds.get("joseph_giggle")),player.getSoundSource(),player.getX(),player.getY(),player.getZ(),1,1,player.getRandom().nextLong()));
                }
            }
            return false;
        });
    }
    public static void onDeath(LivingEntity dead){
        if(!(dead.level() instanceof ServerLevel level)||!HamonService.hasHamon(dead)||HamonConfig.server().keepHamonOnDeath()&&!level.getLevelData().isHardcore())return;
        var hamon=HamonService.state(dead);
        if(hamon.hasSkill("crimson_bubble")){
            var bubble=new CrimsonBubbleEntity(level);ItemStack held=dead.getMainHandItem();
            if(!held.isEmpty()){
                dead.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var item=new ItemEntity(level,dead.getX(),dead.getEyeY()-.3,dead.getZ(),held);item.setPickUpDelay(2);level.addFreshEntity(item);bubble.putItem(item);
            }
            ClackersItem.voice(dead,"caesar_last_hamon");bubble.setPos(dead.getX(),dead.getEyeY(),dead.getZ());bubble.setYRot(dead.getYRot());bubble.setXRot(dead.getXRot());
            bubble.setHamonPoints(hamon.strengthPoints(),hamon.controlPoints());level.addFreshEntity(bubble);
        }else if(hamon.hasSkill("deep_pass")){
            var center=dead.getBoundingBox().getCenter();
            var closest=level.getEntitiesOfClass(ServerPlayer.class,new net.minecraft.world.phys.AABB(center.subtract(8,8,8),center.add(8,8,8)),p->p!=dead&&HamonService.hasHamon(p)).stream().min(java.util.Comparator.comparingDouble(p->p.distanceToSqr(dead))).orElse(null);
            if(closest!=null){
                ClackersItem.voice(dead,"zeppeli_deep_pass");var receiver=HamonService.state(closest);
                if(receiver.technique().equals("jonathan"))ClackersItem.voice(closest,"jonathan_deep_pass_reaction");
                HamonService.setStats(closest,receiver.strengthPoints()+hamon.strengthPoints(),receiver.controlPoints()+hamon.controlPoints());
                HamonAdvancements.onLastHamon(closest,dead);VampireHamonParticles.emit(closest,1);
            }
        }
    }
    private HamonInheritance(){}
}
