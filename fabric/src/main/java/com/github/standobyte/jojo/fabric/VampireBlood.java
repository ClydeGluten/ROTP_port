package com.github.standobyte.jojo.fabric;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Original BleedingEffect and blood-splash acquisition. Ordinary hits do not invent bleeding. */
public final class VampireBlood {
    public static final Holder<MobEffect> BLEEDING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            RipplesFabric.id("bleeding"), new MobEffect(MobEffectCategory.HARMFUL, 0x8b0000) {
                @Override public void onEffectStarted(LivingEntity entity, int amplifier) {
                    entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
                    if (entity.level() instanceof ServerLevel level) splash(level, entity.getBoundingBox().getCenter(), amplifier + 1, 4F * (amplifier + 1), entity);
                }
            }.addAttributeModifier(Attributes.MAX_HEALTH, RipplesFabric.id("bleeding"), -4, AttributeModifier.Operation.ADD_VALUE));
    public static boolean canBleed(LivingEntity entity) {
        if (entity.typeHolder().is(EntityTypeTags.UNDEAD)) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            return entity instanceof Player || entity instanceof net.minecraft.world.entity.monster.zombie.Zombie && !type.equals("husk")
                    || type.equals("zoglin") || type.equals("zombie_horse");
        }
        return entity instanceof Player || entity instanceof AgeableMob || entity instanceof Npc
                || entity instanceof AbstractIllager || entity instanceof WaterAnimal;
    }
    public static int limitAmplifier(LivingEntity entity, int amplifier) {
        var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        double withoutBleeding = attribute.getValue();
        var modifier = attribute.getModifier(RipplesFabric.id("bleeding"));
        if (modifier != null) withoutBleeding -= modifier.amount();
        return Math.min(amplifier, Math.max((int)(attribute.getBaseValue()/4)-2, (int)(withoutBleeding/4)-2));
    }
    public static void bleed(LivingEntity entity, int duration, int amplifier) {
        if (!canBleed(entity)) return;
        int bounded = limitAmplifier(entity, amplifier);
        if (bounded >= 0) entity.addEffect(new MobEffectInstance(BLEEDING, duration, bounded));
    }
    public static void splash(ServerLevel level, Vec3 position, double radius, float amount) {
        splash(level, position, radius, amount, null);
    }
    public static void splash(ServerLevel level, Vec3 position, double radius, float amount, LivingEntity source) {
        VampireService.splashBlood(level, position, radius);
        VampireBloodParticles.spawn(level, position, Math.min((int)(amount*5), 50), .375F, source);
    }
    public static void initialize() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,actual,blocked)->{
            if (!(entity.level() instanceof ServerLevel level) || actual<.98F || !canBleed(entity)
                    || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && !source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS)) return;
            splash(level,entity.getBoundingBox().getCenter(),2,actual,entity);
        });
    }
    private VampireBlood() { }
}
