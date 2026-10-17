package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.model.MagiciansRedModel;
import com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedEntity.Task;
import net.minecraft.util.Mth;

/** Literal original MR and inherited HumanoidStandModel rotations. */
public final class MagiciansRedAnimation {
    private record Rotation(String part,float x,float y,float z,boolean wrap){}
    private static Rotation r(String name,double x,double y,double z){return r(name,x,y,z,true);}
    private static Rotation r(String name,double x,double y,double z,boolean wrap){return new Rotation(name,(float)x,(float)y,(float)z,wrap);}
    private static Rotation d(String name,double x,double y,double z){return d(name,x,y,z,true);}
    private static Rotation d(String name,double x,double y,double z,boolean wrap){return r(name,x*Math.PI/180,y*Math.PI/180,z*Math.PI/180,wrap);}
    private static final Rotation[] SUMMON1={r("head",0.0,0.1309,0.0),r("beakUpper",-0.3491,0.0,0.0),r("beakLower",0.5236,0.0,0.0),r("body",0.0,-0.3927,0.0),r("leftArm",0.0,0.0,-2.3562),r("rightArm",0.0,0.0,2.3562),r("leftLeg",0.1745,-0.7854,0.0),r("rightLeg",-1.5708,-0.7854,0.0),r("rightLowerLeg",2.3562,0.0,0.0)};
    private static final Rotation[] SUMMON2={r("head",-0.7854,0.0,0.0),r("beakUpper",-0.3491,0.0,0.0),r("beakLower",0.5236,0.0,0.0),r("leftArm",0.0,2.3562,-2.8798),r("leftForeArm",0.0,0.0,2.3562),r("rightArm",0.0,-2.3562,2.8798),r("rightForeArm",0.0,0.0,-2.3562),r("leftLeg",0.2182,0.0,0.1309),r("rightLeg",0.5236,0.0,-0.1309)};
    private static final Rotation[] FLAME={d("beakUpper",-20,0.0,0.0),d("beakLower",30,0.0,0.0),d("leftArm",-90,60,12.5),r("leftForeArm",-0.2618,0.0,0.5236),r("rightArm",-1.3963,-0.9163,-0.1745),r("rightForeArm",-0.5236,0.0,-0.6981),r("leftLeg",-0.3927,0.0,0.0873),r("leftLowerLeg",0.7854,0.0,0.0),r("rightLeg",0.2618,0.0,-0.0873)};
    private static final Rotation[] KICK1={d("head",-15,0,0),d("body",-49.1066,-20.7048,22.2077),d("leftLeg",45,30,0),d("rightLeg",-75,30,30),d("rightLowerLeg",60,0,0)};
    private static final Rotation[] KICK2={d("head",-30,-15,0),d("body",-54.7356,-30,35.2644),r("beakUpper",-0.3491,0.0,0.0),r("beakLower",0.5236,0.0,0.0),d("leftArm",-60,0.0,-45),d("leftForeArm",0.0,0.0,50),d("rightArm",45,-10,10),d("rightForeArm",0,0,0),d("leftLeg",45,45,0),d("rightLeg",-105,30,30),d("rightLowerLeg",90,0,0)};
    private static final Rotation[] KICK3={d("head",-45,-10,0),d("body",-59.3179,-27.034,37.4537),d("leftArm",-135,-15,30),d("rightArm",-180,30,-45,false),d("rightForeArm",0,0,-45),d("leftLeg",50,45,0),d("rightLeg",-42.6168,9.6867,44.9391),d("rightLowerLeg",0,0,0)};
    private static final Rotation[] BIND={r("leftArm",-1.4708,0.4712,0.0),r("leftForeArm",0.0,0.0,0.0),r("rightArm",-1.6708,-0.4712,0.0),r("rightForeArm",0.0,0.0,0.0)};
    private static final Rotation[] IDLE={r("beakUpper",0.0,0.0,0.0),r("beakLower",0.0,0.0,0.0),r("body",0.0,0.1309,0.0),r("upperPart",0.0,0.0,0.0),r("leftArm",0.3054,0.0,-0.2182),r("leftForeArm",-2.0944,-0.2618,1.0472),r("rightArm",0.1309,0.0,0.4363),r("rightForeArm",-2.3562,0.2618,-1.8326),r("leftLeg",0.1309,-0.1309,0.0),r("leftLowerLeg",0.2618,0.0,0.0),r("rightLeg",0.0,-0.1309,0.2182),r("rightLowerLeg",0.1309,0.0,0.0)};
    private static final Rotation[] IDLE_LOOP={r("leftArm",0.3927,0.0,-0.1745),r("leftForeArm",-1.9635,-0.1309,1.0472),r("rightArm",0.1309,0.0,0.3054),r("rightForeArm",-2.3562,0.2618,-1.7017)};
    private static final Rotation[] JAB1={d("body",0,0,0),d("upperPart",0,-15,0),d("leftArm",-7.5,0,-15),d("leftForeArm",-100,15,7.5),d("rightArm",22.5,0,22.5),d("rightForeArm",-105,0,-15)};
    private static final Rotation[] JAB2={d("body",0,-5,0),d("upperPart",0,-20,0),d("leftArm",30,0,-15),d("leftForeArm",-107.5,15,7.5),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[] JAB3={d("body",0,-12.5,0),d("upperPart",0,-17.5,0),d("leftArm",37.5,0,-15),d("leftForeArm",-115,15,7.5),d("rightArm",-81.9244,11.0311,70.2661),d("rightForeArm",0,0,0)};
    private static final Rotation[] JAB4={d("body",0,-3.75,0),d("upperPart",0,-3.75,0),d("leftArm",5.63,0,-20.62),d("leftForeArm",-103.75,3.75,13.13),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[] HEAVY1={d("body",0,15,0),d("upperPart",0,15,0),d("leftArm",-90,0,-90),d("leftForeArm",0,0,0),d("rightArm",22.5,0,60),d("rightForeArm",-135,0,0)};
    private static final Rotation[] HEAVY2={d("body",0,26.25,0),d("upperPart",0,26.25,0),d("leftArm",-67.5,0,-90),d("leftForeArm",0,0,0),d("rightArm",30,0,60),d("rightForeArm",-120,0,0)};
    private static final Rotation[] HEAVY3={d("body",0,-26.25,0),d("upperPart",0,-26.25,0),d("leftArm",22.5,0,-60),d("leftForeArm",-135,3.75,13.13),d("rightArm",-67.5,0,90),d("rightForeArm",0,0,0)};
    private static void pose(MagiciansRedModel model,Rotation[] rotations,float amount){pose(model,rotations,amount,false);}
    private static void pose(MagiciansRedModel model,Rotation[] rotations,float amount,boolean mirror){
        for(var r:rotations){String name=r.part;if(mirror)name=name.startsWith("right")?"left"+name.substring(5):name.startsWith("left")?"right"+name.substring(4):name;
            model.rotation(name,r.x,mirror?-r.y:r.y,mirror?-r.z:r.z,amount,r.wrap);}
    }
    private static void head(MagiciansRedModel model,MagiciansRedRenderer.State state,float amount){model.rotation("head",state.xRot*Mth.DEG_TO_RAD,state.yRot*Mth.DEG_TO_RAD,0,amount,true);model.part("head").zRot=0;}
    public static boolean flamePose(Task task){return task==Task.FLAME_BURST||task==Task.FIREBALL||task==Task.CROSSFIRE||task==Task.CROSSFIRE_SPECIAL;}
    public static void apply(MagiciansRedModel model,MagiciansRedRenderer.State state){
        model.part("leftArm").setPos(6,-10,0);model.part("rightArm").setPos(-6,-10,0);model.rotation("beakUpper",10*Mth.DEG_TO_RAD,0,0,1,true);
        float p=Mth.clamp(state.actionTicks/Math.max(1,state.actionDuration),0,1),pitch=state.xRot*Mth.DEG_TO_RAD;
        if(state.task==Task.NONE){
            if(state.summonAnimation&&state.ageInTicks<=20&&state.summonMode==0){pose(model,state.summonVariant%2==0?SUMMON1:SUMMON2,1);float fade=Mth.clamp((state.ageInTicks-15)/5,0,1);pose(model,IDLE,fade);head(model,state,fade);}
            else{pose(model,IDLE,1);head(model,state,1);pose(model,IDLE_LOOP,((float)Math.sin(Math.PI*(state.idleTicks/40-.5))-1)/2);}return;
        }
        pose(model,IDLE,1);head(model,state,1);
        if(flamePose(state.task)){
            pose(model,FLAME,1);model.part("leftArm").setPos(4,-10,0);model.part("rightArm").setPos(-4,-10,0);model.rotation("head",pitch-.3927F,0,0,1,true);
            if(state.actionPhase==3){pose(model,IDLE,p);head(model,state,p);}return;
        }
        if(state.task==Task.RED_BIND){pose(model,BIND,1);if(state.actionPhase==3)pose(model,IDLE,p);return;}
        if(state.task==Task.KICK){
            pose(model,KICK1,1);if(state.actionPhase==1)pose(model,KICK2,p);
            else{pose(model,KICK2,1);pose(model,KICK3,state.actionPhase==2?p:1);if(state.actionPhase==3){pose(model,IDLE,p);model.rotation("rightArm",.1309F,0,.4363F,p,false);}}
            return;
        }
        if(state.task==Task.BLOCK){
            model.rotation("body",0,0,0,1,true);model.rotation("upperPart",0,0,0,1,true);
            model.rotation("rightForeArm",-Mth.HALF_PI,Mth.PI/6,-Mth.HALF_PI,1,true);model.rotation("leftForeArm",-Mth.HALF_PI,-Mth.PI/6,Mth.HALF_PI,1,true);
            float b=Mth.clamp(pitch,-Mth.PI/3,Mth.PI/3)/2;model.rotation("rightArm",-1.5708F+b,-b/2,-Math.abs(b)/2+.7854F,1,true);model.rotation("leftArm",-1.5708F+b,b/2,Math.abs(b)/2-.7854F,1,true);return;
        }
        boolean mirror=state.leftPunch;
        if(state.task==Task.HEAVY){
            pose(model,HEAVY1,1,mirror);if(state.actionPhase==1){if(p<.95F){float t=p/.95F;pose(model,HEAVY2,t*t,mirror);}else{pose(model,HEAVY2,1,mirror);float t=(p-.95F)/.05F;pose(model,HEAVY3,t*t*t,mirror);}}
            else{pose(model,HEAVY3,1,mirror);if(state.actionPhase==3)pose(model,IDLE,Math.max(2*p-1,0));}
            model.secondPitch("leftArm",pitch);model.secondPitch("rightArm",pitch);return;
        }
        if(state.task==Task.LIGHT){
            float armPitch=0;pose(model,JAB1,1,mirror);
            if(state.actionPhase==1){if(p<.5F){pose(model,JAB2,p*2,mirror);armPitch=p*2;}else{pose(model,JAB2,1,mirror);float t=Mth.clamp((p-.5F)*4,0,1);pose(model,JAB3,t*t*t,mirror);armPitch=1;}}
            else if(state.actionPhase==2){pose(model,JAB3,1,mirror);armPitch=1;if(p>=.25F&&p<.5F){float t=(p-.25F)*4;pose(model,JAB4,t*t*t,mirror);armPitch=1-t*t*t;}else if(p>=.5F){pose(model,JAB4,1,mirror);pose(model,JAB1,(p-.5F)*2,!mirror);armPitch=0;}}
            else{pose(model,JAB1,1,!mirror);if(p>.75F)pose(model,IDLE,(p-.75F)*4);}
            model.secondPitch("leftArm",pitch*armPitch);model.secondPitch("rightArm",pitch*armPitch);
        }
    }
    /** Source ribbon origin uses linear raw Euler interpolation, not the posed model's angle wrapping. */
    public static net.minecraft.world.phys.Vec3 kickRibbonOrigin(com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedEntity stand,boolean left,float partial){
        float p=Mth.clamp((stand.actionTicks()+partial)/Math.max(1,stand.actionDuration()),0,1);String arm=left?"leftArm":"rightArm",forearm=left?"leftForeArm":"rightForeArm";
        var body=kickRotation("body",stand.actionPhase(),p);var upper=kickRotation(arm,stand.actionPhase(),p);var lower=kickRotation(forearm,stand.actionPhase(),p);
        var shoulder=rotate(new net.minecraft.world.phys.Vec3(left?-.3515625:.3515625,-.1171875,0),body,left);
        var limb=rotate(rotate(new net.minecraft.world.phys.Vec3(0,-.234375,0),body,left),upper,left);
        var fore=rotate(rotate(rotate(new net.minecraft.world.phys.Vec3(0,-.234375,0),body,left),upper,left),lower,left);
        return stand.getPosition(partial).add(0,stand.getBbHeight()*.75,0).add(shoulder.add(limb).add(fore).yRot((180-Mth.rotLerp(partial,stand.yRotO,stand.getYRot()))*Mth.DEG_TO_RAD));
    }
    private static Rotation find(Rotation[] set,String name){for(var r:set)if(r.part.equals(name))return r;return null;}
    private static Rotation kickRotation(String part,int phase,float p){
        var a=find(phase==1?KICK1:KICK2,part);if(a==null)a=find(IDLE,part);var b=find(phase==1?KICK2:KICK3,part);if(b==null)b=find(KICK2,part);if(b==null)b=a;if(phase==3)p=1;
        return r(part,Mth.lerp(p,a.x,b.x),Mth.lerp(p,a.y,b.y),Mth.lerp(p,a.z,b.z));
    }
    private static net.minecraft.world.phys.Vec3 rotate(net.minecraft.world.phys.Vec3 v,Rotation r,boolean left){return v.zRot(left?r.z:-r.z).yRot(-r.y).xRot(r.x);}
    private MagiciansRedAnimation(){}
}
