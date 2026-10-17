package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.SpeciesHooks;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.3's Smite uses a data predicate rather than the old CreatureAttribute.UNDEAD. */
@Mixin(EntityTypePredicate.class)
abstract class SpeciesEntityPredicateMixin {
    @Inject(method = "matches(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)Z", at = @At("HEAD"), cancellable = true)
    private void jojo$undeadClassification(Entity entity, ServerLevel level, Vec3 origin, CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof LivingEntity living && SpeciesHooks.undeadPlayer(living)) {
            var key = ((EntityTypePredicate) (Object) this).types().unwrapKey();
            if (key.filter(tag -> tag.equals(EntityTypeTags.SENSITIVE_TO_SMITE)
                    || tag.equals(EntityTypeTags.UNDEAD) || tag.equals(EntityTypeTags.INVERTED_HEALING_AND_HARM)).isPresent()) result.setReturnValue(true);
        }
    }
}
