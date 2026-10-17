package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Regression for the two pre-drop hooks and resource-only item consumption. */
public final class HamonLifecycleGameTests {
    private static final Set<UUID> CANCELLED=new HashSet<>();
    static {ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{
        if(!CANCELLED.contains(entity.getUUID()))return true;
        entity.setHealth(1);return false;
    });}
    private static void prepare(LivingEntity entity){
        HamonService.grant(entity);HamonService.setStats(entity,100,200);HamonService.addSkill(entity,"crimson_bubble");
        entity.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(HamonItems.CLACKERS));
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void acceptedDeathCapturesHeldItemAndCancelledDeathDoesNot(GameTestHelper test) throws Exception {
        var field=HamonConfig.class.getDeclaredField("server");field.setAccessible(true);var original=HamonConfig.server();
        var player=VampireGameTests.player(test,"hamon-death",1.5);var mob=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(3.5,2,4));
        // Scope the alternate server option to this synchronous test; always restore shared test configuration.
        field.set(null,new HamonConfig.Data(false,original.hamonTempleSpawn(),original.hamonDamageMultiplier(),original.hamonEnergyTicksDown(),
                original.hamonPointsMultiplier(),original.breathingTrainingMultiplier(),original.breathingTrainingDeterioration(),original.breathingHamonStatGap(),original.mixHamonTechniques(),original.techniqueSkillsRequirement()));
        try {
            prepare(player);CANCELLED.add(player.getUUID());
            player.hurtServer(test.getLevel(),player.damageSources().generic(),1000);
            test.assertTrue(player.isAlive()&&player.getMainHandItem().is(HamonItems.CLACKERS),"A later death veto keeps the held item");
            test.assertTrue(test.getLevel().getEntitiesOfClass(CrimsonBubbleEntity.class,player.getBoundingBox().inflate(3)).isEmpty(),"A later death veto creates no inheritance bubble");
            CANCELLED.remove(player.getUUID());player.damageCooldownTime=0;player.setInvulnerableTime(0);
            player.hurtServer(test.getLevel(),player.damageSources().generic(),1000);
            var bubbles=test.getLevel().getEntitiesOfClass(CrimsonBubbleEntity.class,player.getBoundingBox().inflate(3));
            test.assertTrue(bubbles.size()==1&&bubbles.getFirst().strengthPoints()==100,"Accepted player death creates exactly one stored-Hamon bubble");
            test.assertTrue(bubbles.getFirst().getPassengers().stream().anyMatch(e->e instanceof ItemEntity item&&item.getItem().is(HamonItems.CLACKERS)),"Player held item is captured before inventory drops");
            for(var bubble:bubbles){for(var rider:java.util.List.copyOf(bubble.getPassengers()))rider.discard();bubble.discard();}
            prepare(mob);mob.hurtServer(test.getLevel(),mob.damageSources().generic(),1000);
            bubbles=test.getLevel().getEntitiesOfClass(CrimsonBubbleEntity.class,mob.getBoundingBox().inflate(3));
            test.assertTrue(bubbles.size()==1&&bubbles.getFirst().getPassengers().stream().anyMatch(e->e instanceof ItemEntity item&&item.getItem().is(HamonItems.CLACKERS)),"LivingEntity death captures equipment before mob drops too");
            for(var bubble:bubbles){for(var rider:java.util.List.copyOf(bubble.getPassengers()))rider.discard();bubble.discard();}
            test.succeed();
        } finally {
            field.set(null,original);CANCELLED.remove(player.getUUID());HamonService.clear(player);HamonService.clear(mob);mob.discard();
            player.level().getServer().getPlayerList().remove(player);
        }
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void clackerChargingUsesStabilityWhenEnergyIsEmpty(GameTestHelper test){
        var player=VampireGameTests.player(test,"hamon-clackers",1.5);HamonService.grant(player);HamonService.addSkill(player,"clacker_volley");
        var stack=new ItemStack(HamonItems.CLACKERS);player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.startUsingItem(InteractionHand.MAIN_HAND);
        HamonItems.CLACKERS.onUseTick(test.getLevel(),player,stack,72000);
        test.assertTrue(player.isUsingItem(),"Clacker charging continues with zero energy and available breath stability");
        test.assertTrue(Math.abs(HamonService.state(player).breathStability()-998)<.001,"Five-energy tick consumes exactly two stability");
        player.stopUsingItem();HamonService.clear(player);player.level().getServer().getPlayerList().remove(player);test.succeed();
    }
}
