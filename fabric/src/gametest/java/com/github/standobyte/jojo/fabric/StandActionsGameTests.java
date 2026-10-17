package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandProgress;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** One live old/new SP attack trace plus persistent ownership/held-input security boundaries. */
public final class StandActionsGameTests {
    private static final Identifier PUNCH=RipplesFabric.id("star_platinum_punch");
    private static final Identifier GUARD=RipplesFabric.id("test_registry_guard");
    private static ServerPlayer player(GameTestHelper test,String name,double x){
        var player=VampireGameTests.player(test,name,1.5);player.snapTo(test.absoluteVec(new Vec3(x,1,1.5)),0,15);
        StandService.grant(player);player.setAttached(StandAttachments.PROGRESS,new StandProgress(StandProgress.STAR_PLATINUM,100,0,true));
        test.assertTrue(StandService.toggle(player),"Fixture summons actual SP entity");return player;
    }
    private static void close(ServerPlayer player){StandActionRegistry.cancel(player);StandService.dismiss(player);player.level().getServer().getPlayerList().remove(player);}
    @GameTest(environment="jojo:vampire_night",setupTicks=5,maxTicks=40)
    public void registeredAndLegacyPunchHaveIdenticalAuthoritativeEffects(GameTestHelper test){
        var oldPlayer=player(test,"legacy-action",1.5);var newPlayer=player(test,"registered-action",5.5);
        var oldTarget=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(1.5,1,3.5));var newTarget=test.spawnWithNoFreeWill(EntityTypes.COW,new Vec3(5.5,1,3.5));
        oldTarget.setNoGravity(true);newTarget.setNoGravity(true);
        test.assertTrue(StandActionRegistry.dispatchLegacy(oldPlayer,StandActionPayload.Action.LIGHT_PUNCH),"Legacy enum enters common authority");
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),test.getLevel().registryAccess());
        StandActionPayload.Registered intent;
        try{StandActionPayload.Registered.CODEC.encode(bytes,new StandActionPayload.Registered(PUNCH,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY));intent=StandActionPayload.Registered.CODEC.decode(bytes);}finally{bytes.release();}
        test.assertTrue(StandActionRegistry.dispatch(newPlayer,intent.action(),intent.phase(),intent.shift(),intent.extra()),"Decoded registered intent starts same existing punch");
        test.assertValueEqual(oldPlayer.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),90F,"Legacy punch pays exactly10");
        test.assertValueEqual(newPlayer.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),90F,"Registered delegated punch does not double-charge");
        test.runAtTickTime(9,()->{
            test.assertValueEqual(oldTarget.getHealth(),oldTarget.getMaxHealth()-4,"Legacy path lands original4damage");
            test.assertValueEqual(newTarget.getHealth(),oldTarget.getHealth(),"Registered path hits identically in actual world");
            oldTarget.discard();newTarget.discard();close(oldPlayer);close(newPlayer);test.succeed();
        });
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void registeredHoldPersistsTrainingCooldownPartsAndRejectsStaleRelease(GameTestHelper test){
        if(StandActionRegistry.definition(GUARD).isEmpty())StandActionRegistry.register(
                StandActionRegistry.Definition.builder(GUARD,StandProgress.STAR_PLATINUM).parts(StandParts.Part.ARMS)
                        .hold(20,0,false).cost(5,1).cooldown(2,9).training(0,true,5).movement(.3F).build(),new StandActionRegistry.Handler(){
                    @Override public boolean execute(StandActionRegistry.Context c){return StandService.startHeld(c.player(),true);}
                    @Override public float trainingGain(StandActionRegistry.Context c){return 1;}
                    @Override public void release(StandActionRegistry.Context c,int ticks,boolean cancelled){StandService.stopHeld(c.player());}
                });
        var player=player(test,"registered-save",1.5);
        test.assertTrue(StandActionRegistry.dispatch(player,GUARD,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Registered handler starts real guard");
        StandActionRegistry.tick(player);StandActionRegistry.tick(player);
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),93F,"Authority charges initial5 plus two hold ticks");
        test.assertFalse(StandActionRegistry.dispatch(player,PUNCH,StandActionRegistry.Phase.RELEASE,false,StandActionRegistry.Extra.EMPTY),"Stale different-action release cannot stop current hold");
        test.assertTrue(StandActionRegistry.held(player).orElseThrow().equals(GUARD),"Mismatched release leaves active action intact");
        StandActionRegistry.dispatch(player,GUARD,StandActionRegistry.Phase.RELEASE,false,StandActionRegistry.Extra.EMPTY);
        test.assertTrue(StandActionRegistry.held(player).isEmpty(),"Matching release clears held lifecycle");
        test.assertValueEqual(AbilityCooldowns.remaining(player,GUARD),11,"Source technical+additional cooldown applied once");
        test.assertValueEqual(StandActionTraining.points(player,GUARD,5),1F,"Only successful server action awards training");
        StandParts.remove(player,StandParts.Part.ARMS);
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,test.getLevel().registryAccess());player.saveWithoutId(output);
        AbilityCooldowns.reset(player);StandActionTraining.clear(player);StandParts.reset(player);
        player.load(TagValueInput.create(ProblemReporter.DISCARDING,test.getLevel().registryAccess(),output.buildResult()));
        test.assertValueEqual(AbilityCooldowns.remaining(player,GUARD),11,"Actual player save/load restores action identifier cooldown");
        test.assertValueEqual(StandActionTraining.points(player,GUARD,5),1F,"Actual player save/load retains learned points");
        test.assertFalse(StandParts.has(player,StandParts.Part.ARMS),"Actual player save/load retains stolen arms");
        test.assertFalse(StandActionRegistry.dispatch(player,PUNCH,StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Missing arms block registered original punch");
        test.assertFalse(StandActionRegistry.dispatchLegacy(player,StandActionPayload.Action.LIGHT_PUNCH),"Legacy wire cannot bypass the same part gate");
        var hud=StandHudState.captureRegistered(player);var state=hud.actions().stream().filter(a->a.action().equals(GUARD)).findFirst().orElseThrow();
        test.assertTrue(state.remaining()==11&&state.training()==1&&!state.parts()&&!state.available(),"Owner snapshot exposes actual persisted unavailable state");
        close(player);test.succeed();
    }
    @GameTest(environment="jojo:vampire_night",setupTicks=5)
    public void unknownForgedAndWrongOwnerIntentsDoNotMutateWorld(GameTestHelper test){
        var player=player(test,"registered-reject",1.5);float stamina=player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina();
        test.assertFalse(StandActionRegistry.dispatch(player,RipplesFabric.id("unknown_action"),StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Unknown ID rejected");
        test.assertFalse(StandActionRegistry.dispatch(player,RipplesFabric.id("the_world_punch"),StandActionRegistry.Phase.PRESS,false,StandActionRegistry.Extra.EMPTY),"Another Stand's registered action rejected");
        var forged=new StandActionRegistry.Extra(Optional.of(Identifier.withDefaultNamespace("wither")),Optional.of(java.util.UUID.randomUUID()),true);
        test.assertFalse(StandActionRegistry.dispatch(player,PUNCH,StandActionRegistry.Phase.PRESS,false,forged),"Handler does not accept arbitrary selection/item/modifier data");
        test.assertValueEqual(player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina(),stamina,"Rejected intents spend no stamina");
        test.assertValueEqual(StandService.currentManifestation(player).actionPhase(),0,"Rejected intents do not start attacks");
        var malformed=new RegistryFriendlyByteBuf(Unpooled.buffer(),test.getLevel().registryAccess());boolean rejected=false;
        try{malformed.writeByte(99);try{StandActionPayload.Registered.CODEC.decode(malformed);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}}
        finally{malformed.release();}
        test.assertTrue(rejected,"Unknown schema version fails bounded decoder");close(player);test.succeed();
    }
}
