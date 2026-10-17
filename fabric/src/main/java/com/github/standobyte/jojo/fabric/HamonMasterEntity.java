package com.github.standobyte.jojo.fabric;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
/** Original stationary, persistent Hamon teacher. */
public final class HamonMasterEntity extends Mob {
    public HamonMasterEntity(EntityType<? extends HamonMasterEntity> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE,3).add(Attributes.ATTACK_SPEED,8).add(Attributes.MOVEMENT_SPEED,.1);}
    @Override protected void registerGoals(){}
    @Override public void tick(){super.tick();if(!level().isClientSide()&&!HamonService.hasHamon(this))HamonService.grantMaster(this);}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){if(!HamonService.hasHamon(this))HamonService.grantMaster(this);HamonService.interactTeacher(server,this);}
        return InteractionResult.SUCCESS;
    }
    @Override public boolean canStandOnFluid(FluidState state){return HamonService.state(this).hasSkill("liquid_walking");}
    @Override public void lavaHurt(){}
    @Override public boolean isInvulnerableTo(ServerLevel level,DamageSource source){return source.getMsgId().startsWith("hamon")||super.isInvulnerableTo(level,source);}
}
