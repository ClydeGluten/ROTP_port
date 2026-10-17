package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.world.item.*;
@Mixin(net.minecraft.world.entity.player.Player.class)
abstract class TrackingPlayerAmmoMixin {
 @Inject(method="getProjectile",at=@At("RETURN"),cancellable=true) private void jojo$preferredAmmo(ItemStack weapon,CallbackInfoReturnable<ItemStack> cir){
  if(weapon.getItem() instanceof ProjectileWeaponItem item)cir.setReturnValue(ItemTrackingService.preferredProjectile((net.minecraft.world.entity.player.Player)(Object)this,cir.getReturnValue(),item.getAllSupportedProjectiles()));
 }
}
