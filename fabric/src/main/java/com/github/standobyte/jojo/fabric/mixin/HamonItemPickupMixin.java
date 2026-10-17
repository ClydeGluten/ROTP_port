package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.VampireCombatService;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
abstract class HamonItemPickupMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)private void jojo$stunnedPickup(Player player,CallbackInfo ci){if(VampireCombatService.isStunned(player))ci.cancel();}
}
