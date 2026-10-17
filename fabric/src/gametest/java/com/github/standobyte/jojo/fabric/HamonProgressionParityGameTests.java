package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.Map;

/** Source refresh timing and the real service's final exercise tick at the day boundary. */
public final class HamonProgressionParityGameTests {
    private static ServerPlayer player(GameTestHelper test,String name) {
        var player=VampireGameTests.player(test,name,1.5);
        HamonService.grant(player);player.setOnGround(true);player.setAirSupply(player.getMaxAirSupply());
        return player;
    }
    private static Object runtime(ServerPlayer player) throws ReflectiveOperationException {
        var field=HamonService.class.getDeclaredField("RUNTIME");field.setAccessible(true);
        return ((Map<?,?>)field.get(null)).get(player);
    }
    private static void runtimeValue(ServerPlayer player,String name,Object value) throws ReflectiveOperationException {
        var runtime=runtime(player);var field=runtime.getClass().getDeclaredField(name);field.setAccessible(true);field.set(runtime,value);
    }
    private static HamonProgress almostComplete(float breathing) {
        var state=HamonProgress.initial().withBreathing(breathing);
        for(var exercise:HamonProgress.Exercise.values())state=state.exercise(exercise,state.exerciseMaxTicks(exercise)-(exercise==HamonProgress.Exercise.MEDITATION?1:0));
        return state;
    }
    private static void cleanup(ServerPlayer player) {
        HamonService.clear(player);player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void finalMeditationTickCountsBeforeDayRollover(GameTestHelper test) throws Exception {
        var player=player(test,"hamon-final-tick");
        try {
            player.setAttached(HamonAttachments.PROGRESS,almostComplete(10));
            HamonService.setMeditating(player,true);runtimeValue(player,"meditationTicks",39);
            runtimeValue(player,"day",player.level().getOverworldClockTime()/24000-2);
            HamonService.tick(player);
            var state=HamonService.state(player);
            test.assertTrue(state.skipDays()==2,"The ending day's fourth completion grants both skipped days");
            test.assertTrue(state.completedExercises()==0 && state.effectiveCompletedExercises()==0,"Rollover clears counters and effective bonuses after the last exercise tick");
            test.succeed();
        } finally {cleanup(player);}
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void firstTickInitializesDayAndClockJumpAppliesOneRollover(GameTestHelper test) throws Exception {
        var player=player(test,"hamon-day-init");
        try {
            player.setAttached(HamonAttachments.PROGRESS,HamonProgress.initial().withBreathing(10));
            HamonService.tick(player);
            test.assertTrue(HamonService.state(player).breathingLevel()==10,"First tick does not apply offline penalties");
            var before=HamonService.state(player);var config=HamonConfig.server();
            float expected=before.nextDay(false,config.breathingTrainingDeterioration(),config.breathingTrainingMultiplier()).breathingLevel();
            runtimeValue(player,"day",player.level().getOverworldClockTime()/24000-5);
            HamonService.tick(player);
            test.assertTrue(HamonService.state(player).breathingLevel()==expected,"Multi-day clock difference applies one training day");
            HamonService.tick(player);
            test.assertTrue(HamonService.state(player).breathingLevel()==expected,"Same-day subsequent ticks do not repeat rollover");
            test.succeed();
        } finally {cleanup(player);}
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void cappedSessionSaveReloadAndNetworkKeepDistinctCacheSemantics(GameTestHelper test) throws Exception {
        var player=player(test,"hamon-cap-cache");
        try {
            player.setAttached(HamonAttachments.PROGRESS,almostComplete(100).withStats(40,40).learn("zoom_punch").withEnergy(25));
            HamonService.setMeditating(player,true);runtimeValue(player,"meditationTicks",39);
            HamonService.tick(player);
            var session=HamonService.state(player);
            test.assertTrue(session.completedExercises()==4 && session.effectiveCompletedExercises()==0,"At level 100 actual exercise completion leaves the runtime bonuses stale");
            test.assertTrue(session.fullEnergyTicks()==40,"Uninterrupted capped meditation still charges in 40 ticks");
            test.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RipplesFabric.id("hamon_running_complete"))==null,"Raw running completion does not grant its cached modifier");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),test.getLevel().registryAccess());
            try {
                HamonAttachments.STREAM_CODEC.encode(buffer,session);
                test.assertValueEqual(HamonAttachments.STREAM_CODEC.decode(buffer),session,"Network preserves authoritative stale cache instead of applying a save-load refresh");
            } finally {buffer.release();}
            var encoded=HamonAttachments.CODEC.encodeStart(JsonOps.INSTANCE,session).getOrThrow();
            test.assertFalse(encoded.getAsJsonObject().has("effectiveExerciseMask"),"Runtime cache is absent from persistent data");
            var loaded=HamonAttachments.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
            test.assertValueEqual(loaded,session.refreshExerciseBonuses(),"Old save fields preserve all state and recompute only the effective cache");
            player.setAttached(HamonAttachments.PROGRESS,loaded);HamonService.tick(player);
            test.assertTrue(HamonService.state(player).fullEnergyTicks()==20,"Source-equivalent load refresh enables the capped meditation bonus");
            test.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RipplesFabric.id("hamon_running_complete"))!=null,"Reload restores the completed running modifier");
            HamonService.trainingDay(player);
            test.assertTrue(HamonService.state(player).fullEnergyTicks()==40 && HamonService.state(player).effectiveCompletedExercises()==0,"Daily reset removes loaded bonuses at the cap");
            test.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RipplesFabric.id("hamon_running_complete"))==null,"Daily reset removes the running modifier");
            test.succeed();
        } finally {cleanup(player);}
    }
}
