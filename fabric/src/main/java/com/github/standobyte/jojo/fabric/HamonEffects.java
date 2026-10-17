package com.github.standobyte.jojo.fabric;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
/** Original inherited immobilize/stun effects, without adding an extra status. */
public final class HamonEffects {
    public static final Holder<MobEffect> SHOCK=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("hamon_shock"),new VampireCombatService.StunEffect(0xFFC10A){
        @Override public boolean applyEffectTick(ServerLevel level,LivingEntity e,int a){
            super.applyEffectTick(level,e,a);
            e.setYRot(e.getYRot()+(e.getRandom().nextFloat()-.5F)*(a+1)*2.5F);
            e.yHeadRot+=(e.getRandom().nextFloat()-.5F)*(a+1)*2.5F;
            e.setXRot(e.getXRot()+(e.getRandom().nextFloat()-.5F)*(a+1)*2.5F);
            VampireHamonParticles.emit(e,1);return true;
        }
    });
    public static final Holder<MobEffect> IMMOBILIZE=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("immobilize"),new VampireCombatService.ImmobilizeEffect(0x404040));
    public static final Holder<MobEffect> HYPNOSIS=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("hypnosis"),new MobEffect(MobEffectCategory.HARMFUL,0x998CBC){});
    public static void initialize(){
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((p,l,h,t)->VampireCombatService.isStunned(p)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((p,l,h)->VampireCombatService.isStunned(p)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p,l,h,e,t)->VampireCombatService.isStunned(p)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((p,l,h,b,d)->VampireCombatService.isStunned(p)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
    }
    private HamonEffects(){}
}
