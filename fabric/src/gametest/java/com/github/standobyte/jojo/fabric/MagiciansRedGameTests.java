package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import com.github.standobyte.jojo.fabric.stand.magiciansred.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Two grouped world scenarios. Root owns bootstrap and the consolidated runtime lane. */
public final class MagiciansRedGameTests {
    private static ServerPlayer player(GameTestHelper test){
        var player=VampireGameTests.player(test,"magicians-red",1.5);
        var progress=new StandProgress(MagiciansRedActions.ID,0,0,true);player.setAttached(StandAttachments.PROGRESS,progress.withStamina(progress.maxStamina()));
        ProgressionService.setLevel(player,4);refill(player);StandActionRegistry.unlockAvailable(player);return player;
    }
    private static void refill(ServerPlayer player){var p=player.getAttachedOrCreate(StandAttachments.PROGRESS);player.setAttached(StandAttachments.PROGRESS,p.withStamina(p.maxStamina()));}
    private static MagiciansRedEntity stand(ServerPlayer player){if(!StandService.toggle(player))throw new AssertionError("MR factory summon failed");var stand=(MagiciansRedEntity)StandService.currentManifestation(player);for(int i=0;i<7;i++)tick(stand);return stand;}
    private static void tick(Entity entity){entity.commonTick();entity.tick();}
    private static void idle(MagiciansRedEntity stand){for(int i=0;i<80&&stand.task()!=MagiciansRedEntity.Task.NONE;i++)tick(stand);}
    private static void cleanup(ServerPlayer player,MagiciansRedEntity stand){
        StandActionRegistry.cancel(player);for(var entity:player.level().getEntities(stand,player.getBoundingBox().inflate(32),e->e instanceof MRProjectileEntity||e instanceof MRDetectorEntity))entity.discard();
        stand.discard();player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void flameCostsFireTerrainDetectorAndDistinctExtinguishing(GameTestHelper test){
        var player=player(test);var stand=stand(player);var victim=test.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(2.5,2,3.5));victim.setNoGravity(true);
        try{
            float before=player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
            test.assertTrue(StandActionRegistry.dispatch(player,MagiciansRedActions.FLAME_BURST,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Real registry starts sustained flame");tick(stand);
            test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),before-3,"One perform tick spends source3 stamina");
            var flame=player.level().getEntitiesOfClass(MRFlameEntity.class,stand.getBoundingBox().inflate(8)).getFirst();
            test.assertTrue(Math.abs(flame.getDeltaMovement().length()-1)<.00001,"Resolve3 doubles reach-derived flame velocity");
            test.assertFalse(flame.canBeEvaded(victim),"Source flame cannot be evaded");
            float health=victim.getHealth();flame.setPos(victim.getEyePosition().add(-1,0,0));flame.setDeltaMovement(2,0,0);tick(flame);
            test.assertTrue(victim.getHealth()<health&&victim.getRemainingFireTicks()>0,"Actual flame collision damages and burns living target");
            test.assertTrue(StandActionRegistry.dispatch(player,MagiciansRedActions.DETECTOR,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Detector can run during held flame");
            test.assertValueEqual(StandActionRegistry.held(player).orElseThrow(),MagiciansRedActions.FLAME_BURST,"Detector keeps original held intent and heartbeat");
            test.assertValueEqual(stand.task(),MagiciansRedEntity.Task.FLAME_BURST,"Detector leaves source Stand task intact");
            var detector=player.level().getEntitiesOfClass(MRDetectorEntity.class,player.getBoundingBox().inflate(8)).getFirst();tick(detector);
            test.assertTrue(detector.detected(),"Detector finds eligible world target");
            victim.setPos(detector.position());victim.clearFire();tick(detector);test.assertTrue(victim.getRemainingFireTicks()>0,"Detector burns overlapping nearest target");
            test.assertTrue(StandActionRegistry.dispatch(player,MagiciansRedActions.DETECTOR,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Second detector press toggles off");
            test.assertTrue(detector.isRemoved(),"Owned detector removed");StandActionRegistry.cancel(player);
            var ice=new BlockPos(3,4,3);test.setBlock(ice,Blocks.ICE);
            test.assertTrue(MRFlameEntity.meltIceAndSnow(player.level(),test.getBlockState(ice),test.absolutePos(ice)),"Source ice path runs");
            test.assertTrue(test.getBlockState(ice).is(Blocks.WATER),"Full ice becomes water outside ultrawarm dimension");
            test.setBlock(new BlockPos(2,1,1),Blocks.STONE);player.level().setBlockAndUpdate(player.blockPosition(),MagiciansRedFireBlock.BLOCK.defaultBlockState());
            MagiciansRedActions.removeFireUnderPlayer(player);test.assertTrue(player.level().isEmptyBlock(player.blockPosition()),"Active MR removes fire intersecting user body");
            var water=new BlockPos(0,2,6);test.setBlock(water,Blocks.WATER);var submerged=test.absoluteVec(new Vec3(.5,2.1,6.5));
            var fireball=new MRFireballEntity(stand);fireball.setPos(submerged);player.level().addFreshEntity(fireball);fireball.baseTick();tick(fireball);
            test.assertTrue(fireball.isInWater()&&fireball.isAlive(),"Commented fireball water extinguishing remains inactive");fireball.discard();
            var wetFlame=new MRFlameEntity(stand);wetFlame.setPos(submerged);player.level().addFreshEntity(wetFlame);wetFlame.baseTick();tick(wetFlame);test.assertTrue(wetFlame.isRemoved(),"Real water extinguishes sustained flame");
            var ankh=new MRCrossfireEntity(stand);player.level().addFreshEntity(ankh);ankh.clearFire();test.assertTrue(ankh.isRemoved(),"Crossfire active extinguishing removes ankh");
            var extinguished=new MRFlameEntity(stand);player.level().addFreshEntity(extinguished);extinguished.clearFire();test.assertTrue(extinguished.isRemoved(),"Sustained flame extinguishes");
            boolean breaking=player.level().getGameRules().get(JojoGameRules.BREAK_BLOCKS),mayBuild=player.getAbilities().mayBuild;
            try{
                var stone=new BlockPos(4,3,6);
                for(int mode=0;mode<3;mode++){
                    test.setBlock(stone,Blocks.STONE);player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS,mode!=0,player.level().getServer());player.getAbilities().mayBuild=mode!=1;
                    var shot=new MRFireballEntity(stand);shot.setPos(test.absoluteVec(new Vec3(3,3.5,6.5)));shot.setDeltaMovement(2,0,0);player.level().addFreshEntity(shot);tick(shot);
                    test.assertValueEqual(test.getBlockState(stone).is(Blocks.STONE),mode!=2,"Fireball honors break gamerule and user build permission before destruction");shot.discard();
                }
                test.assertTrue(com.github.standobyte.jojo.fabric.stand.restoration.BlockHistory.at(player.level(),test.absolutePos(stone)).wasBlockBroken(test.absolutePos(stone)),"Actual fireball block destruction enters shared restoration history");
            }finally{player.level().getGameRules().set(JojoGameRules.BREAK_BLOCKS,breaking,player.level().getServer());player.getAbilities().mayBuild=mayBuild;}
            test.assertFalse(MagiciansRedActions.BIND_ENTITY.canSerialize(),"Original bind noSave registration");test.assertFalse(MagiciansRedActions.DETECTOR_ENTITY.canSummon(),"Original detector noSummon registration");test.succeed();
        }finally{victim.discard();cleanup(player,stand);}
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void crossfireFormsTrainingBindFinisherAndOwnerLifecycle(GameTestHelper test){
        var player=player(test);var stand=stand(player);var victim=test.spawnWithNoFreeWill(EntityTypes.ZOMBIE,new Vec3(2.5,2,3.5));victim.setNoGravity(true);victim.setNoAi(false);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
        try{
            test.assertTrue(stand.start(MagiciansRedEntity.Task.CROSSFIRE),"Normal Crossfire charges");
            for(int i=0;i<20;i++){stand.refreshHeld();tick(stand);}stand.releaseHeld(false,20);tick(stand);
            var normal=player.level().getEntitiesOfClass(MRCrossfireEntity.class,stand.getBoundingBox().inflate(8)).getFirst();
            test.assertFalse(normal.small(),"Normal form launches one large ankh");test.assertValueEqual(normal.getOwner(),stand,"Ankh owns exact Stand");
            float health=player.getHealth();StandActionTraining.set(player,MagiciansRedActions.CROSSFIRE,.99F);
            normal.setPos(victim.position().add(.75,.5,0));MRCrossfireExplosion.explode(player.level(),normal);
            test.assertValueEqual(player.getHealth(),health,"Resolve4 explosion excludes user");
            test.assertTrue(victim.getHealth()<1000,"Actual source explosion damages target");
            test.assertTrue(StandActionTraining.unlocked(player,MagiciansRedActions.SPECIAL),"Eligible explosion hit completes training and unlocks special");normal.discard();
            refill(player);test.assertTrue(stand.start(MagiciansRedEntity.Task.CROSSFIRE_SPECIAL),"Trained special charges");
            for(int i=0;i<20;i++){stand.refreshHeld();tick(stand);}stand.releaseHeld(false,20);tick(stand);
            var small=player.level().getEntitiesOfClass(MRCrossfireSpecialEntity.class,stand.getBoundingBox().inflate(8));test.assertValueEqual(small.size(),8,"Special launches eight distinct ankhs");
            test.assertTrue(small.stream().allMatch(e->e.target()!=null&&e.small()),"Each special ankh steers toward selected target");small.forEach(Entity::discard);
            // Put target back on the real player aim ray after explosion knockback.
            for(int hit=0;hit<3;hit++){
                victim.setPos(test.absoluteVec(new Vec3(2.5,2,3.5)));victim.setDeltaMovement(Vec3.ZERO);refill(player);
                test.assertTrue(stand.start(MagiciansRedEntity.Task.LIGHT),"Real light punch builds finisher");idle(stand);
            }
            test.assertTrue(stand.finisherCharge()>=.5F,"Perform and entity-hit increments build source finisher");
            victim.setPos(test.absoluteVec(new Vec3(2.5,2,3.5)));test.assertTrue(stand.start(MagiciansRedEntity.Task.RED_BIND),"Bind starts");tick(stand);
            var bind=player.level().getEntitiesOfClass(MRRedBindEntity.class,stand.getBoundingBox().inflate(8)).getFirst();bind.setPos(victim.getEyePosition());tick(bind);
            test.assertValueEqual(bind.attached(),victim,"Real ribbon ray attaches target");test.assertTrue(victim.hasEffect(HamonEffects.IMMOBILIZE),"Bind owns immobilization");
            stand.releaseHeld(false,1);test.assertValueEqual(stand.task(),MagiciansRedEntity.Task.RED_BIND,"Landed bind remains after release with finisher ready");
            test.assertTrue(stand.start(MagiciansRedEntity.Task.KICK),"Bound finisher kick starts");test.assertTrue(bind.kick()&&victim.hasEffect(VampireCombatService.STUN),"Kick changes ribbon and effect to source stun");
            idle(stand);test.assertTrue(bind.isRemoved(),"Kick windup removes ribbon");test.assertFalse(victim.hasEffect(VampireCombatService.STUN)||victim.hasEffect(HamonEffects.IMMOBILIZE),"Owned effects clean up on ribbon removal");
            MagiciansRedActions.toggleDetector(player);var detector=player.level().getEntitiesOfClass(MRDetectorEntity.class,player.getBoundingBox().inflate(8)).getFirst();stand.discard();tick(detector);
            test.assertTrue(detector.isAlive(),"User-owned detector survives Stand dismissal");detector.tickCount=599;tick(detector);test.assertTrue(detector.isRemoved(),"Detector expires at600 source ticks");test.succeed();
        }finally{victim.discard();cleanup(player,stand);}
    }
}
