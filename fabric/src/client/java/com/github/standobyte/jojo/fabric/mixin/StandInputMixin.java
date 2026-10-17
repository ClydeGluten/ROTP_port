package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.StandView;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class StandInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void jojo$anchorInput(CallbackInfo ci) {
        keyPresses = com.github.standobyte.jojo.fabric.client.VampireClient.leapInput(keyPresses);
        keyPresses = com.github.standobyte.jojo.fabric.client.ZombieClient.leapInput(keyPresses);
        keyPresses = com.github.standobyte.jojo.fabric.client.pillarman.PillarmanClient.leapInput(keyPresses);
        keyPresses = com.github.standobyte.jojo.fabric.client.HamonClient.movementInput(keyPresses);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean immobilized = player != null && (com.github.standobyte.jojo.fabric.VampireCombatService.isStunned(player) || player.hasEffect(com.github.standobyte.jojo.fabric.HamonEffects.IMMOBILIZE));
        if (immobilized || StandView.bodyLocked() || com.github.standobyte.jojo.fabric.client.HamonClient.meditating()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
        else moveVector = com.github.standobyte.jojo.fabric.client.StandCamera.movement(moveVector);
        if (com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.movementMultiplier() == 0) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
        if (com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.isPossessing() && keyPresses.shift()) com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.exit();
    }
}
