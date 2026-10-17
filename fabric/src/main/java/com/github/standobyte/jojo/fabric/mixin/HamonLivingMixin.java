package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LivingEntity.class)
abstract class HamonLivingMixin {
    @Shadow protected boolean jumping;
    @Inject(method="tick",at=@At("TAIL"))private void jojo$hamonTick(CallbackInfo ci){var self=(LivingEntity)(Object)this;HamonMovement.tick(self);if(!self.level().isClientSide())VampireHamon.tickHamonSpread(self);}
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)private void jojo$wallTravel(Vec3 input,CallbackInfo ci){if(HamonMovement.travel((LivingEntity)(Object)this,input,jumping))ci.cancel();}
    // Forge SWIM_SPEED has no vanilla counterpart: zero swim input while immobilized.
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)private Vec3 jojo$immobilizedTravel(Vec3 input){var self=(LivingEntity)(Object)this;return (VampireCombatService.isStunned(self) || self.hasEffect(HamonEffects.IMMOBILIZE))?Vec3.ZERO:input;}
    @ModifyVariable(method="calculateFallDamage",at=@At("HEAD"),argsOnly=true)private double jojo$hamonLeapFall(double distance){var self=(LivingEntity)(Object)this;var h=HamonService.state(self);return HamonService.hasHamon(self) && h.hasSkill("jump") && distance>3?Math.max(distance-((h.hasSkill("afterimages")?1.5:1.4)+5)*3,0):distance;}
    @ModifyVariable(method="actuallyHurt",at=@At("HEAD"),argsOnly=true)private float jojo$protection(float damage,ServerLevel level,DamageSource source,float amount){return HamonActionService.reduceDamage((LivingEntity)(Object)this,source,damage);}
    // Forge LivingDamageEvent runs after absorption, including lethal damage.
    @Inject(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void jojo$releaseHamonShock(ServerLevel level,DamageSource source,float amount,CallbackInfo ci){
        ((LivingEntity)(Object)this).removeEffect(HamonEffects.SHOCK);
    }
}
