package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.fabric.pillarman.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Actual action transitions supply the tracking snapshots consumed by local and remote presentation. */
public final class PillarPresentationGameTests {
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void modeTransitionsPublishActualVisualStateAndClearIt(GameTestHelper test){
        var player=VampireGameTests.player(test,"pillar-visual",1.5);
        test.assertTrue(PillarmanService.grant(player),"Fixture acquires actual Pillar power");
        try{
            test.assertTrue(PillarmanBaseActions.receive(player,new PillarmanActionPayload(PillarmanActionPayload.Action.START_STONE_FORM)),"Initial stage1 stone intent starts");
            for(int i=0;i<40;i++){PillarmanBaseActions.keepHeld(player);PillarmanBaseActions.tick(player);}
            test.assertTrue(player.getAttachedOrCreate(PillarmanAttachments.PROGRESS).stoneForm(),"Real held action publishes stone skin state");
            test.assertValueEqual(player.getAttachedOrCreate(PillarmanAttachments.ACTIONS).held(),0,"Completed stone charge clears held animation clock");
            PillarmanService.setMode(player,PillarmanProgress.Mode.LIGHT);
            test.assertFalse(PillarLightActions.flash(player),"Source stone restriction rejects LIGHT action");
            test.assertFalse(player.getAttachedOrCreate(PillarmanAttachments.BLADES),"Rejected LIGHT action cannot show blades");
            PillarmanService.setStoneForm(player,false);
            test.assertTrue(PillarmanBaseActions.receive(player,new PillarmanActionPayload(PillarmanActionPayload.Action.START_LIGHT_FLASH)),"Mode gate permits LIGHT at stage1");
            for(int i=0;i<12;i++){PillarLightActions.keepHeld(player);PillarLightActions.tick(player);}
            test.assertTrue(player.getAttachedOrCreate(PillarmanAttachments.BLADES),"Accepted charge publishes blades to tracking viewers");
            test.assertValueEqual(PillarLightActions.state(player).ticks(),12,"Animation uses actual authoritative phase clock");
            test.assertValueEqual(PillarLightActions.state(player).phase(),PillarLightActions.Phase.FLASH,"LIGHT visual phase is explicit");
            PillarmanService.setMode(player,PillarmanProgress.Mode.WIND);PillarLightActions.tick(player);
            test.assertValueEqual(PillarLightActions.state(player),PillarLightActions.State.EMPTY,"Mode change clears previous LIGHT pose");
            test.assertFalse(player.getAttachedOrCreate(PillarmanAttachments.BLADES),"Mode change removes blades");
            test.assertTrue(PillarWindActions.receive(player,PillarWindActions.Action.START_DIVINE),"WIND is reachable below stage4");
            PillarWindActions.keepHeld(player);PillarWindActions.tick(player);
            test.assertValueEqual(PillarWindActions.state(player).held(),PillarWindActions.Held.DIVINE,"WIND phase drives source pose and aura");
            PillarmanService.clear(player);
            test.assertValueEqual(PillarWindActions.state(player),PillarWindActions.State.EMPTY,"Power cleanup removes tracked WIND presentation");
            test.assertValueEqual(PillarHeatActions.state(player),PillarHeatActions.State.EMPTY,"Power cleanup removes tracked HEAT presentation");
            test.assertFalse(player.getAttachedOrCreate(PillarmanAttachments.BLADES),"Power cleanup removes transient blade geometry");
            test.succeed();
        }finally{PillarmanService.clear(player);player.level().getServer().getPlayerList().remove(player);}
    }
}
