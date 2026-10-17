package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.model.SilverChariotModel;
import com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity.Kind;
import net.minecraft.util.Mth;
import java.util.*;

/** Legacy SilverChariotModel/LegacyStandAnimator poses, including their original easing arithmetic. */
public final class SilverChariotAnimation {
    private record Rotation(String part, float x, float y, float z) { }
    private static Rotation r(String part, double x, double y, double z) { return new Rotation(part, (float)x, (float)y, (float)z); }
    private static Rotation d(String part, double x, double y, double z) { return r(part, x*Math.PI/180, y*Math.PI/180, z*Math.PI/180); }
    private static final Rotation[] START = {d("body",0,0,0),d("upperPart",0,-45,0),d("leftArm",45,0,-60),d("leftForeArm",0,0,0),d("rightArm",89,0,90),d("rightForeArm",-135,0,0),d("rapier",90,0,0)};
    private static final Rotation[] IMPACT = {d("body",0,0,0),d("upperPart",0,-45,0),d("leftArm",45,0,-60),d("leftForeArm",0,0,0),d("rightArm",-90,45,0),d("rightForeArm",0,0,0),d("rapier",90,0,0)};
    private static final Rotation[] DASH = {r("body",0,-.7854,0),r("upperPart",0,-.7854,0),r("leftArm",.2618,0,-.1309),r("leftForeArm",0,0,0),r("rightArm",0,1.5708,1.5708),r("rightForeArm",0,0,0),r("rapier",1.5708,0,0)};
    private static final Rotation[] SWEEP1 = {d("head",0,-45,0),d("body",0,-90,0),d("upperPart",0,-30,0),d("rightArm",0,45,75),d("rightForeArm",0,0,-120),d("rapier",82.5,0,0)};
    private static final Rotation[] SWEEP2 = {d("head",0,-36,0),d("body",0,-69,0),d("upperPart",0,-24,0),d("rightArm",2.5,36,84),d("rightForeArm",0,0,0),d("rapier",90,0,0)};
    private static final Rotation[] SWEEP3 = {d("head",0,0,0),d("body",0,15,0),d("upperPart",0,0,0),d("leftArm",45,-30,-75),d("leftForeArm",0,0,30),d("rightArm",12.5,0,120)};
    private static final Rotation[] BLOCK = {r("leftArm",-.8727,0,-.1745),r("leftForeArm",-1.5708,.2618,0),r("rightArm",.5236,0,.1746),r("rightForeArm",-1.9199,0,0),r("rapier",.829,0,-1.1781)};
    private static final Rotation[] JAB1 = {d("body",0,0,0),d("upperPart",0,-15,0),d("leftArm",-7.5,0,-15),d("leftForeArm",-100,15,7.5),d("rightArm",22.5,0,22.5),d("rightForeArm",-105,0,-15)};
    private static final Rotation[] JAB2 = {d("body",0,-5,0),d("upperPart",0,-20,0),d("leftArm",30,0,-15),d("leftForeArm",-107.5,15,7.5),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[] JAB3 = {d("body",0,-12.5,0),d("upperPart",0,-17.5,0),d("leftArm",37.5,0,-15),d("leftForeArm",-115,15,7.5),d("rightArm",-81.9244,11.0311,70.2661),d("rightForeArm",0,0,0)};
    private static final Rotation[] JAB4 = {d("body",0,-3.75,0),d("upperPart",0,-3.75,0),d("leftArm",5.63,0,-20.62),d("leftForeArm",-103.75,3.75,13.13),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[][] SUMMON = {
        {r("head",-.1745,0,-.0873),r("upperPart",0,-.2618,0),r("leftArm",.7854,-.2618,0),r("rightArm",-1.8326,-.4363,0),r("leftLeg",.2618,0,0),r("rightLeg",.2618,0,.2618),r("rapier",-.3491,2.3562,0)},
        {r("body",0,-.3927,0),r("leftArm",.5236,0,-.9599),r("leftForeArm",0,0,1.9199),r("rightArm",-1.5708,.3927,0),r("rapier",1.5708,0,0)},
        {r("head",.0873,.0436,-.1745),r("body",0,-.3927,-.1309),r("upperPart",0,.3927,0),r("leftArm",.3927,0,0),r("rightArm",.2618,-.2618,1.3963),r("rightForeArm",-2.3562,0,0),r("leftLeg",0,.3927,.0873),r("rightLeg",0,.3927,.2182),r("rightLowerLeg",.3491,0,-.0873),r("rapier",2.0071,0,0)}
    };
    private static Rotation[] idle(boolean rapier, boolean armor) {
        if (rapier && !armor) return new Rotation[]{r("torso",0,0,0),r("body",.2618,.7854,.0436),r("upperPart",0,-.5236,0),r("leftArm",-.6109,.3927,-1.0472),r("leftForeArm",-1.3963,.2618,.2618),r("rightArm",.5236,0,1.3963),r("rightForeArm",-.5236,1.0472,1.0472),r("rapier",0,0,-.1309),r("leftLeg",0,-.1309,0),r("leftLowerLeg",.0873,0,0),r("rightLeg",-.5236,.3927,.3927),r("rightLowerLeg",1.5708,0,0)};
        return new Rotation[]{r("torso",0,0,0),r("body",0,-.2618,0),r("upperPart",0,0,0),r("leftArm",.9163,-.2618,rapier?.1309:-.2182),r("leftForeArm",-1.309,0,0),r("rightArm",rapier?-.5236:.5236,0,rapier?-.2618:.1309),r("rightForeArm",rapier?0:-.7854,0,0),r("rapier",rapier?-.3927:0,rapier?.1309:0,0),r("leftLeg",.1309,-.1309,0),r("leftLowerLeg",.1309,.0873,0),r("rightLeg",-.1745,.1309,0),r("rightLowerLeg",.2618,0,0)};
    }
    private static Rotation[] idleLoop(boolean rapier, boolean armor) {
        if (!rapier) return new Rotation[]{r("leftArm",.9599,-.2618,-.2618),r("leftForeArm",-1.3526,0,0),r("rightArm",.6109,0,.1309),r("rightForeArm",-.8727,0,0)};
        if (armor) return new Rotation[]{r("leftArm",.9599,-.2182,.1309),r("leftForeArm",-1.3526,0,0),r("rightArm",-.5672,0,-.3491)};
        return new Rotation[]{r("leftArm",-.6545,.3927,-1.0472),r("leftForeArm",-1.4399,.2618,.2618),r("rightArm",.5672,0,1.3963),r("rightForeArm",-.5236,1.0472,1.0906)};
    }
    private static void pose(SilverChariotModel model, Rotation[] pose, float amount) { pose(model,pose,amount,false); }
    private static void pose(SilverChariotModel model, Rotation[] pose, float amount, boolean mirror) {
        for (Rotation r:pose) {
            String name=r.part; if(mirror) name=name.startsWith("right")?"left"+name.substring(5):name.startsWith("left")?"right"+name.substring(4):name;
            model.rotation(name,r.x,mirror?-r.y:r.y,mirror?-r.z:r.z,amount);
        }
    }
    private static void head(SilverChariotModel model, SilverChariotRenderer.State s,float amount) { model.rotation("head",s.xRot*Mth.DEG_TO_RAD,s.yRot*Mth.DEG_TO_RAD,0,amount);model.part("head").zRot=0; }
    public static void apply(SilverChariotModel model, SilverChariotRenderer.State s) {
        model.rotation("rapier",Mth.PI/4,0,0,1);
        var idle=idle(s.rapier,s.armor); float p=Mth.clamp(s.actionTicks/Math.max(1,s.actionDuration),0,1); float pitch=s.xRot*Mth.DEG_TO_RAD;
        if(s.kind==Kind.NONE || s.kind==Kind.REMOVE_ARMOR) {
            if(s.summonAnimation && s.ageInTicks<=20 && s.summonMode==0) { pose(model,SUMMON[Math.floorMod(s.summonVariant,3)],1); float f=Mth.clamp((s.ageInTicks-15)/5,0,1); pose(model,idle,f); head(model,s,f); }
            else { pose(model,idle,1); head(model,s,1); pose(model,idleLoop(s.rapier,s.armor),((float)Math.sin(Math.PI*(s.idleTicks/40-.5))-1)/2); }
            return;
        }
        pose(model,idle,1); head(model,s,1);
        if(!s.rapier) { unarmed(model,s,p,pitch,idle); return; }
        switch(s.kind) {
            case LIGHT -> {
                pose(model,START,1);
                if(s.actionPhase==1) pose(model,IMPACT,p<.75F?p*p-2*p+1:16*p*p-24*p+9);
                else { pose(model,IMPACT,1); if(s.actionPhase==3) pose(model,idle,Math.max(4*(p-1)+1,0)); }
                model.secondPitch("leftArm",pitch); model.secondPitch("rightArm",pitch);
            }
            case BARRAGE -> { if(s.actionPhase==4)barrage(model,s.ageInTicks/2,pitch,0); }
            case DASH, RAPIER_LAUNCH -> {
                if(s.kind==Kind.RAPIER_LAUNCH) {
                    for(Rotation r:DASH)if(!r.part.equals("leftForeArm")&&!r.part.equals("rightForeArm"))model.rotation(r.part,r.x,r.y,r.z,p);
                } else pose(model,DASH,1);
                model.part("rightArm").zRot-=pitch;
                if(s.actionPhase==3) pose(model,idle,p);
            }
            case SWEEP -> {
                if(s.actionPhase==1) pose(model,SWEEP1,p);
                else { pose(model,SWEEP1,1); if(s.actionPhase==2) { if(p<.2F)pose(model,SWEEP2,p/.2F); else {pose(model,SWEEP2,1);pose(model,SWEEP3,(p-.2F)/.8F);} }
                    else {pose(model,SWEEP2,1);pose(model,SWEEP3,1);pose(model,idle,p);head(model,s,p);} }
            }
            case BLOCK -> pose(model,BLOCK,1);
            default -> { }
        }
    }
    /** OneHandedBarrageAnimation has a two-tick cycle with a cubic punch and 0.4..0.6 plateau. */
    public static void barrage(SilverChariotModel model,float loop,float pitch,float zRot) {
        float p=loop-(float)Math.floor(loop); float amount=p<.4F?p*2.5F:p>.6F?(1-p)*2.5F:1;
        pose(model,START,1);pose(model,IMPACT,amount*amount*amount);
        model.secondPitch("leftArm",pitch);model.secondPitch("rightArm",pitch);
        model.part("rightArm").zRot+=zRot;
    }
    private static void unarmed(SilverChariotModel model,SilverChariotRenderer.State s,float p,float pitch,Rotation[] idle) {
        if(s.kind==Kind.BLOCK) {
            model.rotation("body",0,0,0,1);model.rotation("upperPart",0,0,0,1);
            pose(model,new Rotation[]{d("rightForeArm",-90,30,-90),d("leftForeArm",-90,-30,90)},1);
            float b=Mth.clamp(pitch,-Mth.PI/3,Mth.PI/3)/2;
            model.rotation("rightArm",-1.5708F+b,-b/2,-Math.abs(b)/2+.7854F,1);
            model.rotation("leftArm",-1.5708F+b,b/2,Math.abs(b)/2-.7854F,1);return;
        }
        if(s.kind!=Kind.UNARMED && s.kind!=Kind.LIGHT)return;
        boolean mirror=s.leftPunch;float armPitch=0;
        pose(model,JAB1,1,mirror);
        if(s.actionPhase==1) {
            if(p<.5F) {pose(model,JAB2,p*2,mirror);armPitch=p*2;}
            else {pose(model,JAB2,1,mirror);float f=Mth.clamp((p-.5F)*4,0,1);pose(model,JAB3,f*f*f,mirror);armPitch=1;}
        } else if(s.actionPhase==2) {
            pose(model,JAB3,1,mirror);armPitch=1;
            if(p>=.25F && p<.5F) {float f=(p-.25F)*4;pose(model,JAB4,f*f*f,mirror);armPitch=1-f*f*f;}
            else if(p>=.5F) {pose(model,JAB4,1,mirror);pose(model,JAB1,(p-.5F)*2,!mirror);armPitch=0;}
        } else {pose(model,JAB1,1,!mirror);if(p>.75F)pose(model,idle,(p-.75F)*4);}
        model.secondPitch("leftArm",pitch*armPitch);model.secondPitch("rightArm",pitch*armPitch);
    }
    private SilverChariotAnimation() { }
}
