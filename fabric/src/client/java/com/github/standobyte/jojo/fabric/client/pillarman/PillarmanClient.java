package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.core.PillarmanProgress;
import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.pillarman.*;
import com.github.standobyte.jojo.fabric.client.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import java.util.List;

/** Shared hotbar/input adapter. Every action clock, target and resource remains server-owned. */
public final class PillarmanClient {
    public static final String POWER_ID="jojo:pillarman";
    private static Object session;
    private static boolean mode,leftConsumed,rightConsumed,middleConsumed,leapPressed;
    private static ActionCatalog.Move queuedLeft,queuedRight,heldLeft,heldRight;
    private static String sentHeld="";
    private static int heartbeat;
    public static PillarmanProgress state(){var p=Minecraft.getInstance().player;return p==null?PillarmanProgress.EMPTY:p.getAttachedOrCreate(PillarmanAttachments.PROGRESS);}
    public static PillarmanAttachments.Actions hud(){var p=Minecraft.getInstance().player;return p==null?PillarmanAttachments.Actions.EMPTY:p.getAttachedOrCreate(PillarmanAttachments.ACTIONS);}
    public static boolean ownsPower(){return state().pillarman();}
    public static boolean playable(){var p=Minecraft.getInstance().player;return p!=null&&p.isAlive()&&!p.isSpectator()&&ownsPower();}
    public static boolean isMode(){return mode&&playable();}
    public static boolean active(){return isMode();}
    public static void toggle(){
        boolean next=!mode;session=Minecraft.getInstance().level;
        StandSelection.closeStandMode();VampireClient.close();ZombieClient.close();HamonClient.close();close();mode=next&&playable();
    }
    public static void close(){mode=false;clearHeld(false);}
    public static void suspendInput(){clearHeld(false);}
    public static List<String> attacks(){return PillarmanActionCatalog.attacks(state());}
    public static List<String> abilities(){return PillarmanActionCatalog.abilities(state());}
    public static String defaultMiddleAction(){return PillarmanActionCatalog.defaultMiddleAction();}
    public static boolean unlocked(String id){var a=PillarmanActionCatalog.get(id);return playable()&&a!=null&&a.unlocked(state());}
    public static ActionCatalog.Move move(String id){var a=PillarmanActionCatalog.get(id);if(a==null)throw new IllegalArgumentException("Unknown Pillar action "+id);return a.move();}
    public static ActionCatalog.Move displayMove(String id){
        var a=PillarmanActionCatalog.resolve(id,state(),Minecraft.getInstance().options.keyShift.isDown());
        if(a==null)throw new IllegalArgumentException("Unknown Pillar action "+id);return a.move();
    }
    public static String heldAction(){
        var p=Minecraft.getInstance().player;if(p==null)return "";
        if(PossessionClient.heldTicks(p)>=0)return "pillarman_hide_in_entity";
        var wind=PillarWindActions.state(p).held();if(wind!=PillarWindActions.Held.NONE)return "pillarman_"+switch(wind){case DIVINE->"divine_sandstorm";case CLOAK->"wind_cloak";case RIFT->"atmospheric_rift";default->throw new AssertionError();};
        var heat=PillarHeatActions.state(p).phase();if(heat!=PillarHeatActions.Phase.NONE)return "pillarman_"+switch(heat){case ERRATIC->"erratic_blaze_king";case GIANT->"giant_carthwheel_prison";case DETONATION->"self_detonation";default->throw new AssertionError();};
        var light=PillarLightActions.state(p).phase();if(light!=PillarLightActions.Phase.NONE)return "pillarman_"+switch(light){case FLASH->"light_flash";case DECOY->"light_flash_decoy";case SLASH->"blade_slash";case DASH_CHARGE,DASH->"blade_dash_attack";case BARRAGE->"blade_barrage";default->throw new AssertionError();};
        return switch(hud().held()){case 1->"pillarman_absorption";case 2->"pillarman_stone_form";case 3->"pillarman_enhanced_senses";case 4->"pillarman_evasion";case 5->"pillarman_unnatural_agility";default->"";};
    }
    public static boolean available(ActionCatalog.Move move){
        var a=PillarmanActionCatalog.get(move.id());if(a==null||!unlocked(move.id()))return false;
        var client=Minecraft.getInstance();var p=client.player;
        if(TimeStopClient.isFrozen(p)||!a.ignoreStun()&&VampireCombatService.isStunned(p)||!a.stone()&&state().stoneForm()
                ||a.freeHand()&&!p.getMainHandItem().isEmpty()||hud().punchTicks()>0||cooldown(a.id())>0)return false;
        String held=heldAction();if(!held.isEmpty()&&!held.equals(a.id())&&!held.equals("pillarman_absorption"))return false;
        if(a.id().equals("pillarman_absorption")&&client.level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return false;
        if(a.id().equals("pillarman_blade_dash_attack")&&!p.onGround())return false;
        if(a.route()==PillarmanActionCatalog.Route.POSSESSION&&!(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity))return false;
        return p.isCreative()||state().energy()>=a.energy()+a.perTick();
    }
    public static int chargeTicks(String id){
        var p=Minecraft.getInstance().player;if(p==null||!heldAction().equals(id))return 0;
        var a=PillarmanActionCatalog.get(id);
        if(a.route()==PillarmanActionCatalog.Route.POSSESSION)return Math.max(0,PossessionClient.heldTicks(p));
        if(a.route()==PillarmanActionCatalog.Route.WIND)return PillarWindActions.state(p).ticks();
        if(a.mode()==PillarmanProgress.Mode.HEAT)return PillarHeatActions.state(p).ticks();
        if(a.mode()==PillarmanProgress.Mode.LIGHT)return PillarLightActions.state(p).ticks();
        return hud().heldTicks();
    }
    public static int chargeDuration(String id){var a=PillarmanActionCatalog.get(id);return a==null?0:a.charge();}
    public static int cooldown(String id){
        var p=Minecraft.getInstance().player;if(p==null)return 0;
        return switch(id){
            case "pillarman_regeneration"->hud().regenerationCooldown();case "pillarman_evasion"->hud().evasionCooldown();
            case "pillarman_horn_attack"->PillarBodyParts.hornCooldown(p);case "pillarman_ribs_blades"->PillarBodyParts.ribsCooldown(p);
            case "pillarman_small_sandstorm"->PillarWindActions.state(p).smallCooldown();case "pillarman_wind_cloak"->PillarWindActions.state(p).cloakCooldown();
            default->AbilityCooldowns.remaining(p,RipplesFabric.id(id));
        };
    }
    public static int cooldownTotal(String id){
        var p=Minecraft.getInstance().player;var a=PillarmanActionCatalog.get(id);if(p==null||a==null||cooldown(id)<=0)return 0;
        int total=AbilityCooldowns.duration(p,RipplesFabric.id(id));return total>0?total:a.cooldown();
    }
    public static boolean greenSelection(String id){return id.equals("pillarman_stone_form")&&state().stoneForm();}
    public static boolean interceptClick(boolean attack){
        if(!(attack?leftConsumed:rightConsumed)){
            String selected=StandSelection.selectedAction(attack);if(selected==null||selected.isEmpty())return false;
            var move=displayMove(selected);
            if(!available(move)||!canSend(PillarmanActionCatalog.get(move.id()))){PillarmanHud.rejected();return false;}
            if(attack){queuedLeft=move;leftConsumed=true;}else{queuedRight=move;rightConsumed=true;}
        }return true;
    }
    public static boolean capturesAttackHeld(){return leftConsumed||queuedLeft!=null||heldLeft!=null;}
    public static boolean interceptMiddle(){
        if(!isMode()||!StandSelection.capturesMouse()||!available(move(defaultMiddleAction()))||!ClientPlayNetworking.canSend(PillarmanActionPayload.TYPE))return false;
        middleConsumed=true;return true;
    }
    public static void releaseMappedKey(InputConstants.Key key){
        var c=Minecraft.getInstance();
        if(c.options.keyAttack.matches(key)){leftConsumed=false;heldLeft=null;if(queuedLeft!=null&&queuedLeft.held()!=0)queuedLeft=null;}
        if(c.options.keyUse.matches(key)){rightConsumed=false;heldRight=null;if(queuedRight!=null&&queuedRight.held()!=0)queuedRight=null;}
        if(c.options.keyPickItem.matches(key))middleConsumed=false;
        if(heldLeft==null&&heldRight==null&&!middleConsumed)stopSent(true);
    }
    public static void tick(Minecraft c,boolean active){
        if(session!=c.level){clearHeld(false);mode=false;session=c.level;leapPressed=false;}
        if(!playable()){close();return;}
        if(c.isPaused())return;
        if(!active||!isMode()||c.gui.screen()!=null||StandSelection.hotbarsDisabled()){clearHeld(false);return;}
        if(queuedLeft!=null){use(queuedLeft);heldLeft=queuedLeft.held()!=0&&leftConsumed?queuedLeft:null;}
        if(queuedRight!=null){use(queuedRight);heldRight=queuedRight.held()!=0&&rightConsumed?queuedRight:null;}
        if(!c.options.keyAttack.isDown()){heldLeft=null;leftConsumed=false;}
        if(!c.options.keyUse.isDown()){heldRight=null;rightConsumed=false;}
        if(!c.options.keyPickItem.isDown())middleConsumed=false;
        var selected=heldRight!=null?heldRight:heldLeft;
        String desired=middleConsumed?defaultMiddleAction():selected==null?"":selected.id();
        if(!desired.equals(sentHeld)){stopSent(desired.isEmpty());sentHeld=desired;heartbeat=0;if(!desired.isEmpty()){StandCamera.aimForAction();start(desired);}}
        else if(!desired.isEmpty()&&++heartbeat>=5){keep(desired);heartbeat=0;}
        queuedLeft=queuedRight=null;
    }
    public static Input leapInput(Input input){
        var c=Minecraft.getInstance();boolean pressed=input.jump()&&input.shift();
        boolean activate=pressed&&!leapPressed&&isMode()&&c.gui.screen()==null&&!state().stoneForm()&&hud().leapCooldown()==0
                &&hud().punchTicks()==0&&heldAction().isEmpty()&&!VampireCombatService.isStunned(c.player)&&!TimeStopClient.isFrozen(c.player)
                &&(c.player.onGround()||c.player.getVehicle()!=null&&c.player.getVehicle().onGround());
        leapPressed=pressed;if(!activate)return input;StandCamera.aimForAction();sendBase("LEAP");
        return new Input(input.forward(),input.backward(),input.left(),input.right(),false,false,input.sprint());
    }
    private static boolean canSend(PillarmanActionCatalog.Action a){return switch(a.route()){case BASE->ClientPlayNetworking.canSend(PillarmanActionPayload.TYPE);case WIND->ClientPlayNetworking.canSend(PillarWindActions.Intent.TYPE);case POSSESSION->ClientPlayNetworking.canSend(PossessionPayload.Intent.TYPE);};}
    private static void use(ActionCatalog.Move move){if(move.held()==0){StandCamera.aimForAction();start(move.id());}}
    private static void start(String id){var a=PillarmanActionCatalog.get(id);switch(a.route()){
        case BASE->sendBase(a.packet());case WIND->PillarWindClient.send(PillarWindActions.Action.valueOf(a.packet()));
        case POSSESSION->{if(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)PossessionClient.start(hit.getEntity());}
    }}
    private static void keep(String id){switch(PillarmanActionCatalog.get(id).route()){
        case BASE->sendBase("KEEP_HELD");case WIND->PillarWindClient.send(PillarWindActions.Action.KEEP_HELD);case POSSESSION->PossessionClient.keepHeld();
    }}
    private static void stopSent(boolean fire){
        if(!sentHeld.isEmpty())switch(PillarmanActionCatalog.get(sentHeld).route()){
            case BASE->sendBase(fire?"RELEASE_HELD":"STOP_HELD");case WIND->PillarWindClient.send(PillarWindActions.Action.STOP_HELD);case POSSESSION->PossessionClient.release();
        }sentHeld="";heartbeat=0;
    }
    private static void clearHeld(boolean fire){stopSent(fire);leftConsumed=rightConsumed=middleConsumed=false;queuedLeft=queuedRight=heldLeft=heldRight=null;}
    private static void sendBase(String packet){if(ClientPlayNetworking.canSend(PillarmanActionPayload.TYPE))ClientPlayNetworking.send(new PillarmanActionPayload(PillarmanActionPayload.Action.valueOf(packet)));}
    private PillarmanClient(){}
}
