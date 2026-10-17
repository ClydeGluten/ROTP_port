package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Real action/attachment/physics integration; numerical progression is tested in core. */
public final class HamonGameTests {
    private static ServerPlayer player(GameTestHelper test){var p=VampireGameTests.player(test,"hamon-action",1.5);HamonService.grant(p);HamonService.energy(p,1000);p.setAirSupply(p.getMaxAirSupply());p.setOnGround(true);return p;}
    private static void close(ServerPlayer p){HamonActionService.clear(p);HamonService.clear(p);p.level().getServer().getPlayerList().remove(p);}
    private static void hold(ServerPlayer p,int ticks){for(int i=0;i<ticks;i++){HamonActionService.handle(p,new HamonActionPayload(HamonActionService.state(p).held(),HamonActionPayload.Operation.KEEP));HamonActionService.tick(p);}}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void liquidWalkingSurvivesSupportingBlockQueries(GameTestHelper test){
        var p=player(test);HamonService.addSkill(p,"liquid_walking");
        var liquidPos=test.absolutePos(new BlockPos(2,1,1));
        p.level().setBlock(liquidPos,Blocks.WATER.defaultBlockState(),3);
        var water=p.level().getFluidState(liquidPos);
        p.setOnGround(false);
        test.assertFalse(p.canStandOnFluid(water),"Vanilla support predicates remain free of Hamon movement side effects");
        p.move(MoverType.SELF,new Vec3(0,-.25,0));
        test.assertTrue(p.onGround(),"Original water walking marks the player grounded");
        test.assertValueEqual(HamonService.state(p).energy(),950F,"Nested collision queries charge the initial fifty energy once");
        for(int i=0;i<3;i++)p.setOnGround(true);
        p.move(MoverType.SELF,new Vec3(0,-.25,0));
        test.assertTrue(p.onGround() && p.getY()>liquidPos.getY()+.5,"Real downward movement lands on the fluid collision surface");
        test.assertValueEqual(HamonService.state(p).energy(),950F,"Grounded support and movement queries cannot charge twice in one tick");
        p.tickCount++;
        p.move(MoverType.SELF,new Vec3(0,-.08,0));
        test.assertTrue(p.onGround(),"Consecutive liquid walking remains available");
        test.assertValueEqual(HamonService.state(p).energy(),949F,"Consecutive tick retains the original one-energy cost");
        HamonMovement.tick(p);
        test.assertTrue(HamonMovement.isWalkingOnLiquid(p),"Contact still synchronizes the walking presentation");
        p.level().setBlock(liquidPos,Blocks.LAVA.defaultBlockState(),3);p.tickCount++;
        p.setOnGround(false);float health=p.getHealth();p.damageCooldownTime=0;
        p.move(MoverType.SELF,new Vec3(0,-.08,0));
        test.assertTrue(p.onGround(),"Lava uses the same safe movement transition");
        test.assertValueEqual(p.getHealth(),health-1,"Unprotected lava retains the original hot-floor damage");
        test.assertValueEqual(HamonService.state(p).energy(),948F,"Lava re-entry also charges once");
        HamonService.energy(p,0);
        double surface=p.getY();p.move(MoverType.SELF,new Vec3(0,-.1,0));
        test.assertTrue(p.getY()<surface&&!p.onGround(),"Empty energy cannot grant fluid support");
        HamonMovement.tick(p);HamonMovement.tick(p);
        test.assertFalse(HamonMovement.isWalkingOnLiquid(p),"Walking presentation clears when contacts stop");
        close(p);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void maliciousAndLockedRequestsCannotSpendEnergy(GameTestHelper test){var p=player(test);float energy=HamonService.state(p).energy();
        test.assertFalse(HamonActionService.start(p,"HAMON_OVERDRIVE"),"Mixed case packet IDs are rejected");
        test.assertFalse(HamonActionService.start(p,"unknown_action"),"Unknown action never enters runtime");
        test.assertFalse(HamonActionService.start(p,"caesar_bubble_cutter"),"Locked technique cannot be forged in packet");
        test.assertValueEqual(HamonService.state(p).energy(),energy,"Rejected requests consume no energy");
        HamonService.setMeditating(p,true);test.assertFalse(HamonActionService.start(p,"hamon_breath"),"Meditation blocks actions");close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void breathLeaseExpiresAndSyncClears(GameTestHelper test){var p=player(test);HamonService.energy(p,0);
        test.assertTrue(HamonActionService.start(p,"hamon_breath"),"Default breathing starts without a skill");
        test.assertTrue(HamonService.isBreathingHeld(p),"Energy generator sees held breath");
        for(int i=0;i<16;i++)HamonActionService.tick(p);
        test.assertTrue(HamonActionService.state(p).held().isEmpty(),"Missing keepalive cancels held action");
        test.assertFalse(HamonService.isBreathingHeld(p),"Cancellation stops progression breathing too");close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void sunlightEarlyReleaseRefundsCharge(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"sunlight_yellow_overdrive");
        test.assertTrue(HamonActionService.start(p,"hamon_sunlight_yellow_overdrive"),"Learned charged attack starts");hold(p,5);
        test.assertValueEqual(HamonService.state(p).energy(),875F,"Five ticks charge five fortieths of maximum stability");
        HamonActionService.release(p,true);test.assertValueEqual(HamonService.state(p).energy(),1000F,"Premature release refunds accumulated energy");
        test.assertTrue(HamonActionService.state(p).continuous().isEmpty(),"No punch occurs before ten-tick minimum");close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void protectionUsesOriginalDamageCost(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"protection");
        var enemy=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(2.5,2,4));
        test.assertTrue(HamonActionService.start(p,"hamon_protection"),"Protection toggles on");float reduced=HamonActionService.reduceDamage(p,p.damageSources().mobAttack(enemy),4);
        test.assertTrue(Math.abs(reduced-2.4F)<.0001F,"Untrained protection reduces physical damage by forty percent");
        test.assertValueEqual(HamonService.state(p).energy(),700F,"Four damage costs 300 energy");close(p);enemy.discard();test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void shockConsumesEnergyOnlyAtTickNine(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"hamon_shock");
        var target=test.spawnWithNoFreeWill(EntityTypes.VILLAGER,new Vec3(2.5,2,4));target.setNoAi(false);target.setNoGravity(true);
        test.assertTrue(HamonActionService.start(p,"hamon_shock"),"Living target accepts shock");
        for(int n=0;n<9;n++)HamonActionService.tick(p);
        test.assertValueEqual(HamonService.state(p).energy(),1000F,"Windup retains energy");test.assertFalse(target.hasEffect(HamonEffects.SHOCK),"Shock does not occur early");
        HamonActionService.tick(p);test.assertValueEqual(HamonService.state(p).energy(),0F,"Shock spends all stored energy");
        test.assertTrue(target.hasEffect(HamonEffects.SHOCK),"Shock effect applied at source hit tick");test.assertValueEqual(target.getEffect(HamonEffects.SHOCK).getDuration(),80,"Zero control and full energy give eighty ticks");close(p);target.discard();test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void soapBottleLauncherSpawnsThirtySixBubbles(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"bubble_launcher");p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(HamonItems.SOAP));
        test.assertTrue(HamonActionService.start(p,"caesar_bubble_launcher"),"Soap starts bubble launcher");hold(p,1);
        int count=p.level().getEntitiesOfClass(HamonProjectileEntity.class,p.getBoundingBox().inflate(3),e->e.kind()==HamonProjectileEntity.Kind.BUBBLE && e.owner()==p).size();
        test.assertValueEqual(count,36,"One soap bottle emits original thirty-six bubbles");test.assertValueEqual(HamonService.state(p).energy(),950F,"One held tick costs fifty energy");
        test.assertFalse(p.getInventory().contains(s->s.is(HamonItems.SOAP)),"Soap is consumed");test.assertTrue(p.getInventory().contains(s->s.is(Items.GLASS_BOTTLE)),"Bottle is returned");
        for(var entity:p.level().getEntitiesOfClass(HamonProjectileEntity.class,p.getBoundingBox().inflate(3),e->e.owner()==p))entity.discard();close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void infusedEntityPersistsAndDamageDropsAfterFirstHit(GameTestHelper test){var p=player(test);var charged=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(2.5,2,4));var target=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(2.5,2,4));
        HamonCharges.charge(charged,p,2,100,200);var charge=charged.getAttached(HamonCharges.CHARGE);
        var encoded=HamonCharge.CODEC.encodeStart(JsonOps.INSTANCE,charge).getOrThrow();var restored=HamonCharge.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        test.assertValueEqual(restored,charge,"Owner, duration and spent points persist through codec");
        float hp=target.getHealth();charge=HamonCharges.contact(charged,charge,null,target.getBoundingBox());test.assertTrue(Math.abs(hp-target.getHealth()-2)<.0001,"First contact uses full charged damage");
        hp=target.getHealth();charge=HamonCharges.contact(charged,charge,null,target.getBoundingBox());test.assertTrue(Math.abs(hp-target.getHealth()-.2F)<.0001,"Further entity contacts use ten percent damage");
        test.assertTrue(charge.gavePoints() && !charge.large(),"First-hit progression and large-charge flags advance");close(p);charged.discard();target.discard();test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void chargeImmunityAndCactusEnergyProtection(GameTestHelper test){var p=player(test);var charged=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(2.5,2,4));HamonCharges.charge(charged,p,1,100,200);
        test.assertFalse(VampireHamon.damage(p.level(),charged,p,10),"Infused organisms are immune to incoming Hamon");
        test.assertTrue(HamonCharges.preventBlockDamage(p,p.damageSources().cactus(),2),"Hamon cancels cactus damage");test.assertValueEqual(HamonService.state(p).energy(),999F,"Cactus protection costs half incoming damage");close(p);charged.discard();test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void projectileShieldReversesFullVelocityAndKeepsOwner(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"projectile_shield");
        test.assertTrue(HamonActionService.start(p,"hamon_projectile_shield"),"Shield starts");var shooter=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(2.5,2,6));var shot=new Snowball(p.level(),shooter,new ItemStack(Items.SNOWBALL));
        shot.setPos(p.getX(),p.getY()+.9,p.getZ()+3);shot.setDeltaMovement(0,0,-2);p.level().addFreshEntity(shot);
        var shield=p.level().getEntitiesOfClass(HamonProjectileShieldEntity.class,p.getBoundingBox().inflate(6),e->e.owner()==p).getFirst();shield.tick();
        test.assertTrue(shot.getDeltaMovement().z==2,"Deflection reverses the whole source velocity");test.assertTrue(shot.getOwner()==shooter,"Deflection retains projectile owner");test.assertValueEqual(HamonService.state(p).energy(),960F,"Deflection costs twenty energy per speed unit");shot.discard();shield.discard();shooter.discard();close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void hypnosisRestoresPreviousOwner(GameTestHelper test){var p=player(test);var wolf=test.spawnWithNoFreeWill(EntityTypes.WOLF,new Vec3(2.5,2,4));
        test.assertTrue(HamonCharges.canHypnotize(wolf,p),"Untamed wolf is valid hypnosis target");HamonCharges.hypnotize(wolf,p,100);
        test.assertTrue(wolf.getOwnerReference()!=null && wolf.getOwnerReference().getUUID().equals(p.getUUID()),"Hypnosis sets player ownership");
        test.assertFalse(HamonCharges.canHypnotize(wolf,p),"Same owner cannot recast hypnosis");wolf.removeEffect(HamonEffects.HYPNOSIS);HamonCharges.tick(wolf);
        test.assertTrue(wolf.getOwnerReference()==null && !wolf.isTame(),"Expiry restores originally untamed animal");close(p);wolf.discard();test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void fullyChargedSunlightStaysHeldUntilRelease(GameTestHelper test){var p=player(test);HamonService.addSkill(p,"sunlight_yellow_overdrive");
        test.assertTrue(HamonActionService.start(p,"hamon_sunlight_yellow_overdrive"),"Charged action starts");hold(p,45);
        test.assertValueEqual(HamonService.state(p).energy(),0F,"Full charge spends exactly stored energy");
        test.assertValueEqual(HamonActionService.state(p).heldTicks(),45,"Full charge remains held after energy reaches zero");
        HamonActionService.release(p,true);test.assertValueEqual(HamonActionService.state(p).continuous(),"hamon_sunlight_yellow_overdrive","Release starts continuous punch");close(p);test.succeed();}
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void shockAppliesImmediatelyAndRestoresMobAi(GameTestHelper test){var mob=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(2.5,2,4));mob.setNoAi(true);
        test.assertFalse(mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(HamonEffects.SHOCK,20)),"Permanent NoAI rejects Hamon Shock");mob.setNoAi(false);
        test.assertTrue(mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(HamonEffects.SHOCK,20)),"Active mob accepts Hamon Shock");
        test.assertTrue(mob.isNoAi(),"Shock stops AI before the first effect tick");test.assertFalse(mob.hasEffect(VampireCombatService.STUN),"Shock remains a single status");
        test.assertTrue(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==0,"Shock applies immobilization immediately");
        mob.removeEffect(HamonEffects.SHOCK);test.assertFalse(mob.isNoAi(),"Removing Shock immediately restores AI");mob.discard();test.succeed();}

    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void shockEndsOnlyWhenDamageReachesHealth(GameTestHelper test){
        var p=player(test);var mob=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(2.5,2,4));mob.setNoAi(false);
        for(LivingEntity victim:java.util.List.<LivingEntity>of(p,mob)){
            victim.hurtServer(test.getLevel(),victim.damageSources().generic(),1);
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(HamonEffects.SHOCK,100));
            victim.hurtServer(test.getLevel(),victim.damageSources().generic(),1);
            test.assertTrue(victim.hasEffect(HamonEffects.SHOCK),"Damage rejected by hit cooldown retains Shock");
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.ABSORPTION,100));
            victim.setAbsorptionAmount(2);victim.damageCooldownTime=0;
            test.assertValueEqual(victim.getAbsorptionAmount(),2F,"Fixture enables absorption capacity before filling it");
            victim.hurtServer(test.getLevel(),victim.damageSources().generic(),1);
            test.assertTrue(victim.hasEffect(HamonEffects.SHOCK),"Fully absorbed damage retains Shock");
            victim.setAbsorptionAmount(0);victim.damageCooldownTime=0;
            victim.hurtServer(test.getLevel(),victim.damageSources().generic(),1);
            test.assertFalse(victim.hasEffect(HamonEffects.SHOCK),"Health damage releases Shock for both player and mob");
        }
        test.assertFalse(mob.isNoAi(),"Damage releases Shock's AI suppression immediately");
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(HamonEffects.SHOCK,100));mob.setHealth(1);mob.damageCooldownTime=0;
        mob.hurtServer(test.getLevel(),mob.damageSources().generic(),2);
        test.assertFalse(mob.hasEffect(HamonEffects.SHOCK),"Lethal damage reaches the same release hook");
        close(p);mob.discard();test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void blockProtectionCountsContactsAndUsesRawEnergy(GameTestHelper test){
        var p=player(test);BlockPos lower=p.blockPosition(),upper=lower.above();
        p.level().setBlock(lower,Blocks.CACTUS.defaultBlockState(),3);p.level().setBlock(upper,Blocks.CACTUS.defaultBlockState(),3);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireCombatService.FREEZE,100,3));
        test.assertValueEqual(HamonService.bloodstreamEfficiency(p),0F,"Fully frozen fixture has no action efficiency");
        test.assertTrue(HamonCharges.preventBlockDamage(p,p.damageSources().cactus(),2),"Raw energy still protects both cactus contacts");
        test.assertValueEqual(HamonService.state(p).energy(),998F,"Each intersecting cactus consumes half incoming damage");
        HamonService.energy(p,.75F);
        test.assertFalse(HamonCharges.preventBlockDamage(p,p.damageSources().cactus(),2),"Insufficient energy does not buy protection");
        test.assertValueEqual(HamonService.state(p).energy(),0F,"Failed protection consumes remaining energy");
        HamonCharges.charge(p,p,1,100,0);
        p.level().setBlock(lower,Blocks.AIR.defaultBlockState(),3);p.level().setBlock(upper,Blocks.AIR.defaultBlockState(),3);
        test.setBlock(new BlockPos(2,1,1),Blocks.DIRT);test.setBlock(new BlockPos(3,1,1),Blocks.DIRT);
        test.setBlock(new BlockPos(2,2,1),Blocks.SWEET_BERRY_BUSH);test.setBlock(new BlockPos(3,2,1),Blocks.SWEET_BERRY_BUSH);
        p.setPos(test.absoluteVec(new Vec3(3,2,1.5)));
        test.assertTrue(HamonCharges.preventBlockDamage(p,p.damageSources().sweetBerryBush(),2),"Charge fallback protects berry contacts");
        test.assertValueEqual(p.getAttached(HamonCharges.CHARGE).remaining(),96,"Two contacts each consume two charge ticks");
        var master=test.spawn(HamonContentEntities.HAMON_MASTER,new Vec3(2.5,2,4));HamonService.grantMaster(master);HamonService.energy(master,0);
        test.assertTrue(HamonCharges.preventBlockDamage(master,master.damageSources().cactus(),2),"Master remains immune with empty energy and no block overlap");
        test.assertValueEqual(HamonService.state(master).energy(),0F,"Master immunity spends no energy");
        p.level().setBlock(lower,Blocks.AIR.defaultBlockState(),3);p.level().setBlock(upper,Blocks.AIR.defaultBlockState(),3);
        master.discard();close(p);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void infusionAndLifeMagnetismRespectTargetsAndHands(GameTestHelper test){
        var p=player(test);HamonService.addSkill(p,"plant_infusion");
        test.setBlock(new BlockPos(2,3,5),Blocks.OAK_LEAVES);
        var bystander=test.spawnWithNoFreeWill(EntityTypes.VILLAGER,new Vec3(2.5,2,3.5));
        test.assertTrue(HamonActionService.target(p).entity()==bystander,"Fixture aims through a mob at living leaves");
        test.assertTrue(HamonActionService.start(p,"hamon_plant_infusion"),"Plant Infusion retains its block target");
        test.assertFalse(bystander.hasAttached(HamonCharges.CHARGE),"Plant skill cannot charge an organism");
        test.assertValueEqual(HamonService.state(p).energy(),800F,"Plant action uses the block cost even with a mob in front");
        bystander.discard();HamonService.addSkill(p,"animal_infusion");
        var undead=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(2.5,2,3.5));
        test.assertFalse(HamonActionService.start(p,"hamon_plant_infusion"),"Organism replacement rejects undead instead of using the leaves behind");
        test.assertFalse(undead.hasAttached(HamonCharges.CHARGE),"Invalid organism stays uncharged");
        test.assertValueEqual(HamonService.state(p).energy(),800F,"Rejected organism consumes no energy");
        undead.discard();HamonService.addSkill(p,"life_magnetism");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        p.getInventory().add(new ItemStack(Blocks.OAK_LEAVES));
        test.assertFalse(HamonActionService.start(p,"hamon_life_magnetism"),"Inventory leaves do not bypass the free main hand");
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Blocks.OAK_LEAVES));
        test.assertTrue(HamonActionService.start(p,"hamon_life_magnetism"),"Leaves in a hand allow original Life Magnetism exemption");
        for(var entity:p.level().getEntitiesOfClass(LeavesGliderEntity.class,p.getBoundingBox().inflate(10)))entity.discard();
        for(var entity:p.level().getEntitiesOfClass(HamonBlockChargeEntity.class,p.getBoundingBox().inflate(10)))entity.discard();
        close(p);test.succeed();
    }
}
