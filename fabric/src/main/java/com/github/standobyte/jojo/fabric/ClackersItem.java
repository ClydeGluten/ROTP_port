package com.github.standobyte.jojo.fabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
public final class ClackersItem extends Item {
    public ClackersItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(HamonService.state(player).hasSkill("clacker_volley")){player.startUsingItem(hand);return InteractionResult.PASS;}
        clack(level,player);return InteractionResult.FAIL;
    }
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){
        if(level.isClientSide())return;
        int ticks=getUseDuration(stack,entity)-remaining;float cost=ticks<=20?5:1;
        if(HamonService.consumeResourceEnergy(entity,cost)<=0){entity.releaseUsingItem();return;}
        if(ticks==20)voice(entity,"joseph_clacker_volley");
    }
    public static int textureVariant(int ticks){return ticks<10?(ticks%20==10?1:0):ticks<20?(ticks%8==4?1:0):2+ticks%2;}
    @Override public boolean releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        int ticks=getUseDuration(stack,entity)-remaining;float power=Math.min(ticks,20)/20F;
        if(power>0&&power<.15F){clack(level,entity);if(level instanceof ServerLevel server){
            entity.hurtServer(server,entity instanceof Player p?entity.damageSources().playerAttack(p):entity.damageSources().mobAttack(entity),1);
            voice(entity,"joseph_oh_no");}}
        else if(power>.5F&&level instanceof ServerLevel server){
            var projectile=new ClackersEntity(server,entity,stack.copyWithCount(1));float speed=power==1?3:power*2;
            projectile.setHamonDamage(speed*.5F);projectile.setHamonEnergySpent(Math.min(ticks,20)*5+Math.max(ticks-20,0));
            projectile.shootFromRotation(entity,entity.getXRot(),entity.getYRot(),0,speed,.5F);server.addFreshEntity(projectile);
            if(!(entity instanceof Player p&&p.isCreative()))stack.shrink(1);
        }
        return power>.5F;
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return ItemUseAnimation.BOW;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
    @Override public void hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity user){
        if(user.level() instanceof ServerLevel level&&HamonService.state(user).hasSkill("clacker_volley")&&HamonService.consumeResourceEnergy(user,200)>0){
            if(VampireHamon.damage(level,target,user,.15F)){target.damageCooldownTime=0;HamonService.gainActionPoints(user,true,200);}}
    }
    public static void voice(LivingEntity user,String sound){if(!user.level().isClientSide())user.level().playSound(null,user.getX(),user.getY(),user.getZ(),JojoSounds.get(sound),user.getSoundSource(),1,1);}
    private static void clack(Level level,LivingEntity user){level.playSound(user instanceof Player p?p:null,user.getX(),user.getY(),user.getZ(),JojoSounds.get("clackers"),user.getSoundSource(),.5F,1+(user.getRandom().nextFloat()-.5F)*.1F);}
}
