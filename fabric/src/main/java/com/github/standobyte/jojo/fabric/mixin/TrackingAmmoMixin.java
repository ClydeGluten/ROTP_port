package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
@Mixin(ProjectileWeaponItem.class)
abstract class TrackingAmmoMixin {
 @Inject(method="useAmmo",at=@At("RETURN")) private static void jojo$oneIdentity(ItemStack weapon,ItemStack ammo,LivingEntity user,boolean secondary,CallbackInfoReturnable<ItemStack> cir){
  var shot=cir.getReturnValue();if(shot.isEmpty())return;
  if(secondary)shot.remove(TrackedItemIdentity.COMPONENT);
  else if(TrackedItemIdentity.get(shot).isPresent()&&shot!=ammo)ammo.remove(TrackedItemIdentity.COMPONENT);
 }
 @Inject(method="createProjectile",at=@At("RETURN")) private void jojo$projectile(Level level,LivingEntity shooter,ItemStack weapon,ItemStack ammo,boolean critical,CallbackInfoReturnable<Projectile> cir){TrackingHooks.projectileCreated(cir.getReturnValue(),ammo,false);}
}
