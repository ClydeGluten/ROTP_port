package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.HamonActionService;
import com.github.standobyte.jojo.fabric.HamonEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
abstract class HamonPlayerDamageMixin {
    @ModifyVariable(method="actuallyHurt",at=@At("HEAD"),argsOnly=true)private float jojo$protection(float damage,ServerLevel level,DamageSource source,float amount){return HamonActionService.reduceDamage((Player)(Object)this,source,damage);}
    @Inject(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void jojo$releaseHamonShock(ServerLevel level,DamageSource source,float amount,CallbackInfo ci){
        ((Player)(Object)this).removeEffect(HamonEffects.SHOCK);
    }
}
