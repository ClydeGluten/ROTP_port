package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.model.HierophantGreenModel;
import com.github.standobyte.jojo.fabric.stand.hierophant.HierophantGreenEntity.Task;
import net.minecraft.util.Mth;

/** Active original legacy pose tables, including its negative idle easing and repeated esPose4 bone. */
public final class HierophantAnimation {
    private record Angle(String bone, float x, float y, float z) { }
    private static Angle r(String bone, float x, float y, float z) { return new Angle(bone,x,y,z); }
    private static Angle d(String bone, float x, float y, float z) { return r(bone,x*Mth.DEG_TO_RAD,y*Mth.DEG_TO_RAD,z*Mth.DEG_TO_RAD); }
    private static final Angle[] IDLE = {
        r("body",0.0F, 0.3491F, 0.0F),
        r("upperPart",0.0F, 0.0F, 0.0F),
        r("leftArm",0.0F, 0.0F, -0.2182F),
        r("leftForeArm",-0.4363F, 0.0F, 0.0F),
        r("rightArm",-0.0873F, 0.0F, 0.7854F),
        r("rightForeArm",-1.9199F, -0.5672F, 0.9599F),
        r("leftLeg",-0.5672F, -0.6109F, -0.1309F),
        r("leftLowerLeg",1.309F, -0.1309F, 0.0F),
        r("rightLeg",0.2182F, 0.1309F, 0.1309F),
        r("rightLowerLeg",0.2182F, 0.1309F, 0.0F)
    };
    private static final Angle[] IDLE2 = {
        r("leftArm",0.0873F, 0.0F, -0.2182F),
        r("leftForeArm",-0.5236F, 0.0F, 0.0F),
        r("rightArm",0.0698F, 0.0F, 0.7854F),
        r("rightForeArm",-2.0071F, -0.4363F, 0.9599F)
    };
    private static final Angle[] SUMMON = {
        r("head",-0.5236F, 0.0F, 0.0F),
        r("leftArm",0.0F, 0.0F, -0.5236F),
        r("leftForeArm",-0.5236F, 0.0F, 1.3963F),
        r("rightArm",-1.2217F, 0.0F, 0.0F),
        r("leftLeg",-1.8326F, 0.0F, 0.2618F),
        r("leftLowerLeg",1.8326F, 0.0F, 0.0F),
        r("rightLeg",0.0F, 0.0F, -0.2618F),
        r("rightLowerLeg",1.5708F, 0.0F, 0.0F)
    };
    private static final Angle[] SPLASH1 = {
        d("leftArm",-55, 0, 15),
        d("leftForeArm",0, -90, 45),
        d("rightArm",-90, 0, 75),
        d("rightForeArm",-52.5F, 0, 0)
    };
    private static final Angle[] SPLASH2 = {
        d("leftArm",-90, 0, 0),
        d("leftForeArm",0, 0, 42.5F),
        d("rightArm",-90, 0, 0),
        d("rightForeArm",0, 0, -42.5F)
    };
    private static final Angle[] SPLASH3 = {
        d("head",0, 22.5F, 0),
        d("body",0, 60F, 0),
        d("upperPart",0, 30F, 0),
        d("leftArm",-90, 0, -56.25F),
        d("leftForeArm",-39.38F, 0, 10.62F),
        d("rightArm",-63.75F, 0, -11.25F),
        d("rightForeArm",0, 67.5F, -44.37F),
        d("leftLeg",0.0F, 0.0F, -42.5F),
        d("leftLowerLeg",0.0F, 0.0F, 120F),
        d("rightLeg",0.0F, 0.0F, 15F),
        r("rightLowerLeg",0.0F, 0.0F, 0.0F)
    };
    private static final Angle[] SPLASH4 = {
        d("head",0, 0, 0),
        d("body",0, 0, 0),
        d("upperPart",0, 0, 0),
        d("leftArm",-90, 0, -75),
        d("rightForeArm",0, 90, -45),
        d("rightArm",-55, 0, -15)
    };
    public static void apply(HierophantGreenModel model, HierophantGreenRenderer.State state) {
        apply(model,IDLE,1); head(model,state,1);
        if (state.summoning) {
            for (String name : new String[]{"head","body","upperPart","leftArm","leftForeArm","rightArm","rightForeArm","leftLeg","leftLowerLeg","rightLeg","rightLowerLeg"}) {
                var part=model.bone(name); part.xRot=part.yRot=part.zRot=0;
            }
            apply(model,SUMMON,1);
            float amount=Math.clamp((state.summonTime-15)/5,0,1);
            apply(model,IDLE,amount); head(model,state,amount); return;
        }
        if (state.task==Task.BLOCK) {
            set(model,"body",0,0,0); set(model,"upperPart",0,0,0);
            apply(model,new Angle[]{d("rightForeArm",-90,30,-90),d("leftForeArm",-90,-30,90)},1);
            float pitch=Math.clamp(state.xRot*Mth.DEG_TO_RAD,-Mth.PI/3,Mth.PI/3)/2;
            set(model,"rightArm",-1.5708F+pitch,-pitch/2,-Math.abs(pitch)/2+.7854F);
            set(model,"leftArm",-1.5708F+pitch,pitch/2,Math.abs(pitch)/2-.7854F); return;
        }
        if (state.task==Task.GRAPPLE || state.task==Task.GRAPPLE_ENTITY) {
            set(model,"rightArm",-1.5708F,0,0); set(model,"rightForeArm",0,0,0); set(model,"body",0,0,0); return;
        }
        if (state.task==Task.SPLASH || state.task==Task.CONCENTRATED) {
            if (state.phase==3) return; // Original recovery is the rigid idle pose, not a new transition.
            float progress=state.phase==2?1:Math.clamp(state.phaseProgress,0,1);
            Angle[][] poses={SPLASH1,SPLASH2,SPLASH3,SPLASH3,SPLASH4};
            float[] times={0,.6F,.85F,.9F,1};
            int segment=0; while(segment<3 && progress>=times[segment+1]) segment++;
            float amount=(progress-times[segment])/(times[segment+1]-times[segment]);
            // ModelPoseTransitionMultiple uses the exact next breakpoint at equality.
            if(progress==times[segment]) amount=times[segment];
            apply(model,poses[segment],1); apply(model,poses[segment+1],amount);
            if(segment==3) head(model,state,amount); return;
        }
        apply(model,IDLE2,((float)Math.sin(Math.PI*(state.idleTime/40-.5F))-1)/2);
    }
    private static void head(HierophantGreenModel model, HierophantGreenRenderer.State state, float amount) {
        var head=model.bone("head"); head.xRot=lerp(amount,head.xRot,state.xRot*Mth.DEG_TO_RAD); head.yRot=lerp(amount,head.yRot,state.yRot*Mth.DEG_TO_RAD);head.zRot=0;
    }
    private static void set(HierophantGreenModel model,String name,float x,float y,float z) { var p=model.bone(name);p.xRot=x;p.yRot=y;p.zRot=z; }
    private static void apply(HierophantGreenModel model,Angle[] pose,float amount) {
        for(var angle:pose){var part=model.bone(angle.bone);part.xRot=lerp(amount,part.xRot,angle.x);part.yRot=lerp(amount,part.yRot,angle.y);part.zRot=lerp(amount,part.zRot,angle.z);}
    }
    private static float lerp(float amount,float from,float to){return from+amount*(Mth.wrapDegrees((to-from)*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD);}
    private HierophantAnimation() { }
}
