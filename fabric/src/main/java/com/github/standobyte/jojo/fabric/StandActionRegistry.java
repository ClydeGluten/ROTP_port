package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.StandDefinitions;
import com.github.standobyte.jojo.core.StandProgress;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-only action authority. Registered IDs describe original actions; handlers resolve world targets themselves. */
public final class StandActionRegistry {
    public enum Hotbar { LEFT, RIGHT, HIDDEN }
    public enum Phase { PRESS, KEEP, RELEASE }
    public record Extra(Optional<Identifier> selection, Optional<UUID> item, boolean recoveryModifier, Optional<String> subtype) {
        public static final Extra EMPTY=new Extra(Optional.empty(),Optional.empty(),false);
        public Extra(Optional<Identifier> selection, Optional<UUID> item, boolean recoveryModifier) {
            this(selection,item,recoveryModifier,Optional.empty());
        }
        public Extra {
            Objects.requireNonNull(selection);Objects.requireNonNull(item);Objects.requireNonNull(subtype);
            selection.ifPresent(StandActionRegistry::checkId);
            if(subtype.isPresent() && (selection.isEmpty() || !subtype.get().matches("[a-z0-9_./-]{1,64}")))
                throw new IllegalArgumentException("Invalid Stand action subtype");
        }
        public boolean isEmpty() { return selection.isEmpty()&&item.isEmpty()&&!recoveryModifier&&subtype.isEmpty(); }
    }
    public record Definition(Identifier id,String standId,Hotbar hotbar,int slot,Set<StandParts.Part> parts,
                             Identifier shift,int maxHoldTicks,int fireAfter,boolean continueHolding,
                             float startCost,float tickCost,int technicalCooldown,int additionalCooldown,
                             float resolveCooldownMultiplier,int resolveLevel,boolean trained,float maximumTraining,
                             float movement,boolean delegated,boolean manifestationRequired,
                             Set<Identifier> recoveryFollowups,Set<Identifier> requiredEffects) {
        public Definition {
            checkId(id);checkId(Identifier.parse(standId));Objects.requireNonNull(hotbar);
            parts=Set.copyOf(parts);recoveryFollowups=Set.copyOf(recoveryFollowups);requiredEffects=Set.copyOf(requiredEffects);
            if(shift!=null)checkId(shift);
            if(slot<0||maxHoldTicks<0||fireAfter<0||technicalCooldown<0||additionalCooldown<0||resolveLevel< -1
                    ||!finiteNonnegative(startCost)||!finiteNonnegative(tickCost)||!finiteNonnegative(maximumTraining)
                    ||!finiteNonnegative(resolveCooldownMultiplier)||!Float.isFinite(movement)||movement<0||movement>1)
                throw new IllegalArgumentException("Invalid Stand action definition");
        }
        public static Builder builder(Identifier action,String standId) { return new Builder(action,standId); }
        public boolean held() { return maxHoldTicks>0||fireAfter>0; }
    }
    public static final class Builder {
        private final Identifier id;private final String stand;
        private Hotbar hotbar=Hotbar.HIDDEN;private int slot,maxHold,fireAfter,technical,additional,resolve;
        private Set<StandParts.Part> parts=Set.of();private Identifier shift;
        private boolean continued,trained,delegated,manifestation=true;private float startCost,tickCost,maximum=1,movement=1,resolveCooldown;
        private Set<Identifier> followups=Set.of(),effects=Set.of();
        private Builder(Identifier id,String stand){this.id=id;this.stand=stand;}
        public Builder hotbar(Hotbar value,int index){hotbar=value;slot=index;return this;}
        public Builder parts(StandParts.Part... value){parts=Set.copyOf(Arrays.asList(value));return this;}
        public Builder shift(Identifier value){shift=value;return this;}
        public Builder hold(int maxTicks,int ticksToFire,boolean continueHolding){maxHold=maxTicks;fireAfter=ticksToFire;continued=continueHolding;return this;}
        public Builder cost(float initial,float perTick){startCost=initial;tickCost=perTick;return this;}
        public Builder cooldown(int technicalTicks,int additionalTicks){technical=technicalTicks;additional=additionalTicks;return this;}
        public Builder resolveCooldownMultiplier(float value){resolveCooldown=value;return this;}
        public Builder training(int level,boolean isTrained,float max){resolve=level;trained=isTrained;maximum=max;return this;}
        public Builder movement(float factor){movement=factor;return this;}
        public Builder delegated(){delegated=true;return this;}
        public Builder requiresManifestation(boolean value){manifestation=value;return this;}
        public Builder recoveryFollowupOf(Identifier... ids){followups=Set.copyOf(Arrays.asList(ids));return this;}
        public Builder requiresEffect(Identifier... ids){effects=Set.copyOf(Arrays.asList(ids));return this;}
        public Definition build(){return new Definition(id,stand,hotbar,slot,parts,shift,maxHold,fireAfter,continued,startCost,tickCost,technical,additional,resolveCooldown,resolve,trained,maximum,movement,delegated,manifestation,followups,effects);}
    }
    public record Context(ServerPlayer player,Definition definition,StandManifestation manifestation,Extra extra) {}
    /** Source onClick prepares a summon before requirements; afterClick commits or discards it. */
    public interface Preparation {
        StandManifestation manifestation();
        boolean finish(boolean success);
        static Preparation existing(StandManifestation manifestation) {
            return new Preparation() {
                public StandManifestation manifestation(){return manifestation;}
                public boolean finish(boolean success){return success;}
            };
        }
    }
    @FunctionalInterface public interface Handler {
        /** No payload extra is accepted by default. Validate selections against server-owned choices and UUID ownership explicitly. */
        default boolean validateExtra(Context context,Extra extra){return extra.isEmpty();}
        default boolean canUse(Context context){return true;}
        default boolean autoSummons(){return false;}
        default boolean canUseWithoutManifestation(Context context){return false;}
        default Preparation prepare(Context context){return Preparation.existing(context.manifestation);}
        default boolean canReplaceHeld(Context context,Identifier activeId){return false;}
        default boolean canRunAlongsideHeld(Context context,Identifier activeId){return false;}
        /** Entity-owned task phases retain registry authority but own their cost, training and recovery timing. */
        default boolean ownsTaskLifecycle(){return false;}
        boolean execute(Context context);
        default boolean startHolding(Context context){return true;}
        default boolean tracksHold(Context context){return context.definition.held();}
        default boolean tick(Context context,int ticks){return true;}
        default void keep(Context context){}
        default void release(Context context,int ticks,boolean cancelled){}
        default float maxTraining(Context context){return context.definition.maximumTraining;}
        default float trainingGain(Context context){return 0;}
        default int cooldown(Context context,int ticks){
            var d=context.definition;int additional=context.player.isCreative()?0:d.additionalCooldown;
            if(StandAttachments.resolve(context.player).isBurst())additional=(int)(additional*d.resolveCooldownMultiplier);
            return (int)Math.min(Integer.MAX_VALUE,(long)d.technicalCooldown+additional);
        }
    }
    private record Registered(Definition definition,Handler handler){}
    private static final Map<Identifier,Registered> ACTIONS=new LinkedHashMap<>();
    private static final Map<String,java.util.function.BiFunction<ServerPlayer,Identifier,Identifier>> REPLACEMENTS=new HashMap<>();
    /** Source replaceAction runs before part/training checks; only same-Stand registered replacements are accepted. */
    public static void registerReplacement(String stand,java.util.function.BiFunction<ServerPlayer,Identifier,Identifier> replacement){
        StandDefinitions.definition(stand);
        if(REPLACEMENTS.putIfAbsent(stand,Objects.requireNonNull(replacement))!=null)throw new IllegalStateException("Duplicate Stand replacement: "+stand);
    }
    private static final Map<UUID,Hold> HELD=new HashMap<>();
    private static final Map<UUID,Identifier> LAST_ACTION=new HashMap<>();
    private static boolean initialized;
    private static final int LEASE_TICKS=12;
    private static final class Hold {
        final Context context;final Handler handler;final Identifier requested;final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        int ticks,lease=LEASE_TICKS;boolean fired;
        Hold(Context context,Handler handler,Identifier requested){this.context=context;this.handler=handler;this.requested=requested;dimension=context.player.level().dimension();}
    }
    private static void checkId(Identifier id){Objects.requireNonNull(id);if(id.toString().length()>256)throw new IllegalArgumentException("Action identifier too long");}
    private static boolean finiteNonnegative(float value){return Float.isFinite(value)&&value>=0;}
    private static void serverThread(ServerPlayer player){if(!player.level().getServer().isSameThread())throw new IllegalStateException("Stand action outside server thread");}
    public static void register(Definition definition,Handler handler){
        Objects.requireNonNull(handler);StandDefinitions.definition(definition.standId);
        if(actions(definition.standId).size()>=512)throw new IllegalStateException("Stand action catalog limit exceeded");
        if(ACTIONS.putIfAbsent(definition.id,new Registered(definition,handler))!=null)throw new IllegalStateException("Duplicate Stand action: "+definition.id);
    }
    public static Optional<Definition> definition(Identifier id){var action=ACTIONS.get(id);return action==null?Optional.empty():Optional.of(action.definition);}
    public static List<Definition> actions(String standId){return ACTIONS.values().stream().map(Registered::definition).filter(d->d.standId.equals(standId)).toList();}
    public static Optional<Identifier> held(ServerPlayer player){var hold=HELD.get(player.getUUID());return hold==null?Optional.empty():Optional.of(hold.context.definition.id);}
    public static int heldTicks(ServerPlayer player){var hold=HELD.get(player.getUUID());return hold==null?0:hold.ticks;}
    public static boolean partsAvailable(ServerPlayer player,Definition definition){return definition.parts.stream().allMatch(p->StandParts.has(player,p));}
    private static boolean owns(Context c){
        var p=c.player;var progress=p.getAttachedOrCreate(StandAttachments.PROGRESS);
        return p.isAlive()&&!p.isSpectator()&&progress.hasStand()&&progress.standId().equals(c.definition.standId)
                &&StandDefinitions.definition(progress.standId()).playable();
    }
    private static boolean eligible(Context c,boolean starting){return eligible(c,starting,false);}
    private static boolean eligible(Context c,boolean starting,boolean preview){
        if(!owns(c)||TimeStopService.isFrozen(c.player)||!partsAvailable(c.player,c.definition))return false;
        var manifestation=StandService.currentManifestation(c.player);
        boolean prospective=preview&&manifestation==null&&ACTIONS.get(c.definition.id).handler.autoSummons();
        if(c.definition.manifestationRequired&&!prospective&&(manifestation==null||manifestation.owner()!=c.player
                ||manifestation.entity().level()!=c.player.level()||!manifestation.definition().id().equals(c.definition.standId)))return false;
        for(var effect:c.definition.requiredEffects)if(StandEffects.entries(c.player).stream().noneMatch(e->e.type().equals(effect)))return false;
        if(!c.definition.delegated){
            if(!StandActionTraining.unlocked(c.player,c.definition.id))return false;
            if(starting&&AbilityCooldowns.remaining(c.player,c.definition.id)>0)return false;
            if(starting&&!ACTIONS.get(c.definition.id).handler.ownsTaskLifecycle()&&manifestation!=null&&manifestation.uiRecoveryTicks()>0
                    &&(!LAST_ACTION.containsKey(c.player.getUUID())||!c.definition.recoveryFollowups.contains(LAST_ACTION.get(c.player.getUUID()))))return false;
        }
        return true;
    }
    private static boolean canPay(Context c,float cost){return StandConfig.staminaFree(c.player)||StandAttachments.resolve(c.player).isBurst()||c.player.getAttachedOrCreate(StandAttachments.PROGRESS).stamina()>=cost;}
    private static void pay(Context c,float cost){if(!StandConfig.staminaFree(c.player)&&cost>0){var progress=c.player.getAttachedOrCreate(StandAttachments.PROGRESS);c.player.setAttached(StandAttachments.PROGRESS,progress.withStamina(Math.max(0,progress.stamina()-cost)));}}
    private static boolean unlockedAtResolve(ServerPlayer player,Definition d){return StandConfig.skipsTraining(player)||d.resolveLevel>=0&&ProgressionService.unlocked(player,d.resolveLevel);}
    public static void unlockAvailable(ServerPlayer player){
        if(!player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand())return;
        for(var d:actions(player.getAttachedOrCreate(StandAttachments.PROGRESS).standId()))if(!d.delegated&&unlockedAtResolve(player,d)){
            var context=new Context(player,d,StandService.currentManifestation(player),Extra.EMPTY);
            float maximum=ACTIONS.get(d.id).handler.maxTraining(context);
            if(finiteNonnegative(maximum))StandActionTraining.unlock(player,d.id,d.trained,maximum);
        }
    }
    public static float maximumTraining(ServerPlayer player,Definition definition){float value=ACTIONS.get(definition.id).handler.maxTraining(new Context(player,definition,StandService.currentManifestation(player),Extra.EMPTY));return finiteNonnegative(value)?value:0;}
    public static boolean available(ServerPlayer player,Definition definition){
        var context=new Context(player,definition,StandService.currentManifestation(player),Extra.EMPTY);
        boolean time=definition.id.getPath().endsWith("_time_stop");
        boolean cooldown=time?TimeStopService.cooldownRemaining(player)>0:AbilityCooldowns.remaining(player,definition.id)>0;
        var handler=ACTIONS.get(definition.id).handler;
        return !cooldown&&eligible(context,true,true)&&(definition.delegated||canPay(context,definition.startCost+definition.tickCost))
                &&(context.manifestation==null&&handler.autoSummons()?handler.canUseWithoutManifestation(context):handler.canUse(context));
    }
    public static boolean dispatch(ServerPlayer player,Identifier id,Phase phase,boolean shift,Extra extra){
        serverThread(player);Objects.requireNonNull(phase);Objects.requireNonNull(extra);checkId(id);
        var active=HELD.get(player.getUUID());
        if(phase!=Phase.PRESS){
            if(active==null||active.context.player!=player||!(active.requested.equals(id)||active.context.definition.id.equals(id))||!extra.isEmpty())return false;
            if(phase==Phase.RELEASE){finish(player,false);return true;}
            if(!validHold(active)){finish(player,true);return false;}
            active.lease=LEASE_TICKS;active.handler.keep(active.context);return true;
        }
        var registered=ACTIONS.get(id);if(registered==null)return false;
        if(shift&&registered.definition.shift!=null){registered=ACTIONS.get(registered.definition.shift);if(registered==null)return false;}
        var replacement=REPLACEMENTS.get(registered.definition.standId);
        if(replacement!=null){
            String stand=registered.definition.standId;
            var replacedId=replacement.apply(player,registered.definition.id);
            registered=replacedId==null?null:ACTIONS.get(replacedId);
            if(registered==null||!registered.definition.standId.equals(stand))return false;
        }
        // StandEntityHeavyAttack replaces its ordinary punch with the usable finisher before part checks.
        // The World can still kick after its arms are taken; the entity selects meter-driven finishers.
        if(registered.definition.id.equals(RipplesFabric.id("the_world_heavy_punch"))
                &&!StandParts.has(player,StandParts.Part.ARMS)&&StandParts.has(player,StandParts.Part.LEGS))
            registered=ACTIONS.get(RipplesFabric.id("the_world_kick"));
        if(registered==null)return false;
        var d=registered.definition;var context=new Context(player,d,StandService.currentManifestation(player),extra);
        if(!owns(context)||!registered.handler.validateExtra(context,extra))return false;
        if(active!=null&&active.context.definition.id.equals(d.id))return false;
        var prepared=registered.handler.prepare(context);
        if(prepared==null)return false;
        boolean succeeded=false;
        try {
            context=new Context(player,d,prepared.manifestation(),extra);
            succeeded=executePress(context,registered,id,active);
        } finally {
            boolean executed=succeeded;
            succeeded=prepared.finish(succeeded);
            if(executed&&!succeeded)finish(player,true);
        }
        return succeeded;
    }
    private static boolean executePress(Context context,Registered registered,Identifier requested,Hold active){
        var player=context.player;var d=context.definition;
        unlockAvailable(player);
        if(!eligible(context,true)||!registered.handler.canUse(context))return false;
        boolean alongside=active!=null&&!d.held()&&registered.handler.canRunAlongsideHeld(context,active.context.definition.id);
        if(active!=null&&!alongside&&!registered.handler.canReplaceHeld(context,active.context.definition.id))return false;
        if(!d.delegated&&!canPay(context,d.startCost+(d.held()?d.tickCost:0)))return false;
        if(active!=null&&!alongside)finish(player,true);
        boolean automatic=!d.delegated&&!registered.handler.ownsTaskLifecycle();
        boolean delayed=d.fireAfter>0&&automatic;
        if(delayed?!registered.handler.startHolding(context):!registered.handler.execute(context))return false;
        if(!delayed&&automatic){pay(context,d.startCost);train(context,registered.handler);}
        LAST_ACTION.put(player.getUUID(),d.id);
        if(d.held()&&registered.handler.tracksHold(context)){
            var hold=new Hold(context,registered.handler,requested);hold.fired=!delayed;HELD.put(player.getUUID(),hold);
            if(automatic)StandMovement.actionFactor(player,d.id,d.movement);
        }else if(automatic)cooldown(context,registered.handler,0);
        return true;
    }
    private static void train(Context c,Handler handler){
        if(!c.definition.trained)return;float gain=handler.trainingGain(c),maximum=handler.maxTraining(c);
        if(finiteNonnegative(gain)&&finiteNonnegative(maximum)&&gain>0)StandActionTraining.add(c.player,c.definition.id,gain,maximum,4);
    }
    private static void cooldown(Context c,Handler handler,int ticks){int duration=handler.cooldown(c,ticks);if(duration>0)AbilityCooldowns.start(c.player,c.definition.id,duration);}
    private static boolean validHold(Hold hold){
        return hold.context.player.level().dimension().equals(hold.dimension)&&eligible(hold.context,false)
                &&hold.handler.validateExtra(hold.context,hold.context.extra)&&hold.handler.canUse(hold.context)
                &&(!hold.context.definition.manifestationRequired||StandService.currentManifestation(hold.context.player)==hold.context.manifestation);
    }
    public static void tick(ServerPlayer player){
        serverThread(player);unlockAvailable(player);var hold=HELD.get(player.getUUID());if(hold==null)return;
        if(TimeStopService.isFrozen(player))return;
        if(!validHold(hold)||--hold.lease<0){finish(player,true);return;}
        var d=hold.context.definition;hold.ticks++;
        // PowerBaseImpl stops at maximum before calling the final onHoldTick.
        if(hold.ticks>=Math.max(d.maxHoldTicks,d.fireAfter)){finish(player,false);return;}
        if(!d.delegated&&!hold.handler.ownsTaskLifecycle()){
            if(!canPay(hold.context,d.tickCost)){finish(player,true);return;}
            if(!hold.handler.tick(hold.context,hold.ticks)){finish(player,true);return;}
            pay(hold.context,d.tickCost);
        }else if(!hold.handler.tick(hold.context,hold.ticks)){finish(player,false);return;}
    }
    private static void finish(ServerPlayer player,boolean cancelled){
        var hold=HELD.remove(player.getUUID());if(hold==null)return;
        var d=hold.context.definition;
        boolean automatic=!d.delegated&&!hold.handler.ownsTaskLifecycle();
        boolean shouldFire=!cancelled&&automatic&&!hold.fired&&hold.ticks>=d.fireAfter&&validHold(hold)&&canPay(hold.context,d.startCost);
        try{hold.handler.release(hold.context,hold.ticks,cancelled);}
        finally{if(automatic)StandMovement.releaseAction(player,hold.context.definition.id);}
        // Source stoppedHolding runs before performAction for charged attacks.
        if(shouldFire&&hold.handler.execute(hold.context)){pay(hold.context,d.startCost);train(hold.context,hold.handler);hold.fired=true;}
        if(automatic&&hold.fired)cooldown(hold.context,hold.handler,hold.ticks);
    }
    public static void cancel(ServerPlayer player){serverThread(player);finish(player,true);LAST_ACTION.remove(player.getUUID());}
    public static void clearRuntime(){HELD.clear();LAST_ACTION.clear();}

    /** Binary-compatible legacy intent entrypoint. Services retain all existing SP/TW costs and timing. */
    public static boolean dispatchLegacy(ServerPlayer player,StandActionPayload.Action action){
        serverThread(player);
        switch(action){
            case TOGGLE -> {cancel(player);return StandService.toggle(player);}
            case STOP_HELD -> {finish(player,false);StandService.stopHeld(player);return true;}
            case KEEP_HELD -> {var hold=HELD.get(player.getUUID());if(hold!=null)return dispatch(player,hold.requested,Phase.KEEP,false,Extra.EMPTY);StandService.refreshHeld(player);return true;}
            case START_CONTROL -> {return StandParts.has(player,StandParts.Part.MAIN_BODY)&&StandService.startControl(player);}
            case STOP_CONTROL -> {StandService.stopControl(player);return true;}
            case CANCEL_TIME_CHARGE -> {TimeStopService.cancelCharge(player);return true;}
            default -> {var id=legacyId(player,action);return id!=null&&dispatch(player,id,Phase.PRESS,false,Extra.EMPTY);}
        }
    }
    public static Identifier legacyId(ServerPlayer player,StandActionPayload.Action action){
        var progress=player.getAttachedOrCreate(StandAttachments.PROGRESS);
        if(!progress.hasStarPlatinum()&&!progress.hasTheWorld())return null;
        String p=progress.hasTheWorld()?"the_world_":"star_platinum_";
        String suffix=switch(action){case LIGHT_PUNCH->"punch";case HEAVY_PUNCH->"heavy_punch";case START_BARRAGE->"barrage";case START_GUARD->"block";case UPPERCUT->progress.hasTheWorld()?"kick":"uppercut";case STAR_FINGER->"star_finger";case START_INHALE->"inhale";case START_ZOOM->"zoom";case TIME_STOP->"time_stop";case BLINK->"ts_blink";case TIME_PUNCH->"ts_punch";default->null;};
        return suffix==null?null:RipplesFabric.id(p+suffix);
    }
    private static Handler delegate(java.util.function.Predicate<ServerPlayer> execute,boolean held){
        return new Handler(){
            @Override public boolean execute(Context c){return execute.test(c.player);}
            @Override public boolean tracksHold(Context c){return held;}
            @Override public void keep(Context c){if(held)StandService.refreshHeld(c.player);}
            @Override public void release(Context c,int ticks,boolean cancelled){if(held)StandService.stopHeld(c.player);}
        };
    }
    private static Builder base(String stand,String action){return Definition.builder(RipplesFabric.id(stand+"_"+action),"jojo:"+stand).delegated();}
    public static void initialize(){
        if(initialized)return;initialized=true;
        for(String stand:List.of("star_platinum","the_world")){
            boolean world=stand.equals("the_world");var arms=StandParts.Part.ARMS;var body=StandParts.Part.MAIN_BODY;
            var heavy=RipplesFabric.id(stand+"_heavy_punch");
            register(base(stand,"punch").hotbar(Hotbar.LEFT,0).parts(arms).shift(heavy).cost(10,0).build(),delegate(p->StandService.attack(p,false),false));
            register(base(stand,"heavy_punch").parts(arms).cost(50,0).movement(.5F).build(),delegate(p->StandService.attack(p,true),false));
            register(base(stand,world?"kick":"uppercut").parts(world?StandParts.Part.LEGS:arms).cost(50,0).training(1,false,1).build(),delegate(StandService::uppercut,false));
            register(base(stand,"barrage").hotbar(Hotbar.LEFT,1).parts(arms).shift(heavy).hold(Integer.MAX_VALUE,0,false).cost(0,4).movement(.15F).build(),delegate(p->StandService.startHeld(p,false),true));
            register(base(stand,"block").hotbar(Hotbar.RIGHT,0).parts(arms).hold(Integer.MAX_VALUE,0,false).movement(.3F).build(),delegate(p->StandService.startHeld(p,true),true));
            // Legacy Time Stop uses its already-shipped training/charge/cooldown service; registry must not charge twice.
            register(base(stand,"time_stop").hotbar(Hotbar.RIGHT,world?1:3).parts(body).shift(RipplesFabric.id(stand+"_time_resume")).hold(0,world?30:40,false).cost(225,9).training(world?2:4,true,100).requiresManifestation(false).build(),delegate(p->{
                StandService.stopControl(p);boolean result=TimeStopService.toggle(p);
                if(!result&&ProgressionService.unlocked(p,p.getAttachedOrCreate(StandAttachments.PROGRESS).timeStopUnlock()))p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jojo.fabric.time_unavailable",TimeStopService.cooldownRemaining(p)/20F));
                return result;},false));
            register(base(stand,"time_resume").requiresManifestation(false).build(),delegate(p->TimeStopService.isActive(p)&&TimeStopService.toggle(p),false));
            register(base(stand,"ts_blink").parts(body).training(world?2:4,true,1).requiresManifestation(false).build(),delegate(TheWorldActions::blink,false));
        }
        register(base("star_platinum","star_finger").hotbar(Hotbar.LEFT,2).parts(StandParts.Part.ARMS).cost(375,0).cooldown(20,60).training(3,false,1).movement(.5F).build(),delegate(p->StandService.special(p,StarPlatinumEntity.PHASE_FINGER),false));
        register(base("star_platinum","inhale").hotbar(Hotbar.RIGHT,2).parts(StandParts.Part.MAIN_BODY).hold(80,0,false).cost(0,2).cooldown(0,80).training(2,false,1).movement(.5F).build(),delegate(p->StandService.special(p,StarPlatinumEntity.PHASE_INHALE),true));
        register(base("star_platinum","zoom").hotbar(Hotbar.RIGHT,1).parts(StandParts.Part.MAIN_BODY).hold(Integer.MAX_VALUE,0,false).movement(.5F).build(),delegate(p->StandService.special(p,StarPlatinumEntity.PHASE_ZOOM),true));
        register(base("the_world","ts_punch").hotbar(Hotbar.LEFT,2).parts(StandParts.Part.MAIN_BODY,StandParts.Part.ARMS).cost(50,0).cooldown(0,50).training(3,false,1).build(),delegate(TheWorldActions::timePunch,false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->server.execute(()->cancel(handler.player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->clearRuntime());
    }
    private StandActionRegistry(){}
}
