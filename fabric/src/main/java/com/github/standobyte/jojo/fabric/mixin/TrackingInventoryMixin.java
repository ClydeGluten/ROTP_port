package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.stand.tracking.TrackingHooks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Vanilla pickup copies into inventory and removes the drop within one call; observe the completed transfer immediately. */
@Mixin(Inventory.class)
abstract class TrackingInventoryMixin {
 @Shadow @Final public Player player;
 @Inject(method="add",at=@At("RETURN")) private void jojo$added(CallbackInfoReturnable<Boolean> cir){TrackingHooks.entityChanged(player);}
 @Inject(method={"setItem","setChanged"},at=@At("TAIL")) private void jojo$changed(CallbackInfo ci){TrackingHooks.entityChanged(player);}
}
