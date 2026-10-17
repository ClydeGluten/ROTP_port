package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.mixin.StandDamageAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Port of DamageUtil.hurtThroughInvulTicks: Stand action timing owns its hit cadence. */
public final class StandDamage {
    /** IStandDamageSource identity survives reflected attacks and carries the original barrage punch budget. */
    public static final class Source extends DamageSource {
        private final DamageSource original;
        private int barrageHits;
        private boolean self;
        public Source(DamageSource original,int barrageHits){super(original.typeHolder(),original.getDirectEntity(),original.getEntity());this.original=original;this.barrageHits=Math.max(0,barrageHits);}
        public int barrageHits(){return barrageHits;}
        public void barrageHits(int count){barrageHits=Math.max(0,count);}
        public Source allowSelf(){self=true;return this;}
        public boolean canHitSelf(){return self;}
        @Override public boolean is(net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag){return original.is(tag);}
        @Override public boolean is(net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key){return original.is(key);}
        @Override public net.minecraft.world.phys.Vec3 getSourcePosition(){return original.getSourcePosition();}
        @Override public float getFoodExhaustion(){return original.getFoodExhaustion();}
        @Override public boolean scalesWithDifficulty(){return false;}
        @Override public String getMsgId(){return original.getMsgId();}
        @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return original.getLocalizedDeathMessage(victim);}
    }
    public static Source marked(DamageSource source,int barrageHits){return new Source(source,barrageHits);}
    public static boolean isStand(DamageSource source){return source instanceof Source||source.getDirectEntity() instanceof StandManifestation
            ||source.getDirectEntity() instanceof PillarmanBaseActions.EvasionProjectile projectile&&projectile.standDamage();}
    public static boolean canTakeDamageFrom(DamageSource source){return isStand(source)||source.getMsgId().contains("stand")
            ||source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
            ||source.getEntity() instanceof LivingEntity attacker&&attacker.hasEffect(StandPerception.INTEGRATED_STAND);}
    /** Original StandEntity.isInvulnerableTo; ordinary weapon contact cannot hurt a manifestation. */
    public static boolean canDamageBody(StandManifestation stand,DamageSource source){
        if(source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))return true;
        var owner=stand.owner();if(owner==null)return false;
        if((source.getEntity()==stand.entity()||source.getEntity()==owner)&&!(source instanceof Source marked&&marked.canHitSelf()))return false;
        if(owner.getAbilities().invulnerable&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        if(source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)&&!owner.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.FIRE_DAMAGE))return false;
        return canTakeDamageFrom(source)&&!stand.entity().isInvulnerable()&&!owner.isInvulnerable();
    }

    /** Original StandEntity.hurtTarget multiplier, separate from linked owner damage. */
    public static boolean standHurt(ServerLevel level,LivingEntity target,DamageSource source,float amount){
        return hurt(level,target,source instanceof Source?source:marked(source,0),amount*StandConfig.server().standPowerMultiplier());
    }
    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        int cooldown = target.damageCooldownTime;
        var access = (StandDamageAccess) target;
        float lastHurt = access.jojo$getLastHurt();
        target.damageCooldownTime = 0;
        try {
            return target.hurtServer(level, source, amount);
        } finally {
            // Keep other attacks' immunity intact, including when a hit is blocked or rejected.
            target.damageCooldownTime = cooldown;
            access.jojo$setLastHurt(lastHurt);
        }
    }

    private StandDamage() {}
}
