package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Three grouped contracts; config changes never escape the synchronous assertion that owns them. */
public final class StandConfigGameTests {
    private static StandConfig.Data settings(boolean skip,boolean stamina,List<Float> thresholds,int range,float frozen,float damage,float resistance){
        return new StandConfig.Data(true,skip,stamina,true,true,false,thresholds,range,frozen,damage,resistance);
    }
    private static void close(ServerPlayer player){StandService.dismiss(player);player.level().getServer().getPlayerList().remove(player);}
    @GameTest(environment="jojo:vampire_night")
    public void changedThresholdsNormalizeSavedStateAndSourceSkipReachesActualActions(GameTestHelper test){
        var previous=StandConfig.server();var player=VampireGameTests.player(test,"stand-settings",1.5);
        try{
            StandConfig.apply(settings(true,false,List.of(6000F),12,1,1,1));
            StandService.grant(player);
            test.assertValueEqual(ProgressionService.state(player).level(),4,"Progression bypass grants original maximum Resolve level");
            test.assertValueEqual(ProgressionService.state(player).maxResolve(),6000F,"Short threshold list uses its last value without reducing max level");
            test.assertValueEqual(ProgressionService.state(player).trainingPoints(),95F,"Bypass fills original human Time Stop training");
            player.setAttached(StandAttachments.PROGRESS,player.getAttachedOrCreate(StandAttachments.PROGRESS).withStamina(0));
            test.assertTrue(StandService.toggle(player)&&StandService.attack(player,false),"Zero-stamina survival player can actually punch with stamina disabled");
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),0F,"Infinite stamina does not debit stored resource");
            var earned=new ResolveProgress(4,5000,100,2,8,List.of(5900F),true,0,0,95);
            var nbt=StandAttachments.RESOLVE_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,earned).getOrThrow();
            StandConfig.apply(settings(false,true,List.of(3000F),12,1,1,1));
            var loaded=StandAttachments.RESOLVE_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,nbt).getOrThrow();
            test.assertValueEqual(loaded.resolve(),3000F,"Lower config clamps old saved points");
            test.assertTrue(loaded.records().equals(List.of(5900F))&&loaded.trainingPoints()==95&&loaded.level()==4,"Earned history, training and level survive changed thresholds");
        }finally{StandConfig.apply(previous);close(player);}
        test.succeed();
    }
    @GameTest(environment="jojo:vampire_night")
    public void extraActivationsAndVisibleBoostsPersistWithoutMultiplyingGain(GameTestHelper test){
        var player=VampireGameTests.player(test,"resolve-extras",1.5);
        try{
            StandService.grant(player);ProgressionService.setLevel(player,4);
            player.addEffect(new MobEffectInstance(StandItems.RESOLVE,600,4));
            test.assertValueEqual(ResolveExtras.state(player).extraLevels().get(StandProgress.STAR_PLATINUM),1,"Actual max-level Resolve effect increments its Stand's extra count");
            player.removeEffect(StandItems.RESOLVE);ProgressionService.update(player,ResolveProgress.EMPTY.withLevel(4).attack(10,20));
            ResolveExtras.chat(player,"five!");
            test.assertValueEqual(ResolveExtras.state(player).chat(),1.25F,"Source combat chat caps at1.25");
            test.assertTrue(StandService.toggle(player)&&StandService.startControl(player),"Real manifestation enters remote control");
            for(int i=0;i<40;i++)ResolveExtras.tick(player,false);
            test.assertTrue(Math.abs(ResolveExtras.state(player).remote()-2)<.001,"Remote display grows by.025 per tick");
            var before=ProgressionService.state(player);var next=before.attack(10,20);
            test.assertValueEqual(next.resolve()-before.resolve(),15F,"Chat and remote display factors do not multiply actual Resolve gains");
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,test.getLevel().registryAccess());
            player.saveWithoutId(output);player.setAttached(ResolveExtras.DATA,ResolveExtras.EMPTY);
            player.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,test.getLevel().registryAccess(),output.buildResult()));
            test.assertTrue(ResolveExtras.state(player).extraLevels().get(StandProgress.STAR_PLATINUM)==1&&ResolveExtras.state(player).chat()==1.25F,"Actual player save/load retains extra count and visible boosts");
            StandService.stopControl(player);ResolveExtras.tick(player,false);
            test.assertValueEqual(ResolveExtras.state(player).remote(),1F,"Leaving remote control resets its display factor");
        }finally{close(player);}
        test.succeed();
    }
    @GameTest(environment="jojo:stand_config_time",maxTicks=100)
    public void dimensionWideTimeAndDamageSettingsReachActualEntities(GameTestHelper test){
        var player=VampireGameTests.player(test,"stand-time-config",1.5);
        player.setGameMode(GameType.CREATIVE);StandService.grant(player);StandService.toggle(player);
        test.assertTrue(TimeStopService.toggle(player),"Starts real Time Stop charge");
        test.startSequence().thenWaitUntil(()->test.assertTrue(TimeStopService.isActive(player),"Charge finishes on server ticks"))
                .thenExecute(()->{
                    var previous=StandConfig.server();
                    try{
                        StandConfig.apply(settings(false,true,StandConfig.DEFAULTS.resolveLvlPoints(),0,.5F,2,3));
                        test.assertTrue(TimeStopService.isTimeStopped(player.level(),new net.minecraft.world.level.ChunkPos(100000,100000)),"Range0 covers whole dimension");
                        var enderman=test.spawnWithNoFreeWill(EntityTypes.ENDERMAN,new Vec3(3,1,3));
                        test.assertFalse(TimeStopService.isFrozen(enderman),"Original enabled Enderman exemption");
                        var cow=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(4,1,4));
                        var source=player.damageSources().playerAttack(player);float health=cow.getHealth();
                        StandDamage.standHurt(player.level(),cow,source,4);
                        test.assertValueEqual(cow.getHealth(),health-4,"Stand damage×2 and frozen damage×.5 each apply once before armor");
                        enderman.discard();cow.discard();
                    }finally{StandConfig.apply(previous);close(player);}
                }).thenSucceed();
    }
}
