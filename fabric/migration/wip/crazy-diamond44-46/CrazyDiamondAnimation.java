package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.client.model.CrazyDiamondModel;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.CrazyDiamondEntity.Kind;
import com.github.standobyte.jojo.fabric.stand.crazydiamond.RestorationSelection.Mode;
import net.minecraft.util.Mth;

/** Literal active source pose tables; wrapped body turns and unwrapped heavy head turns remain distinct. */
public final class CrazyDiamondAnimation {
    private record Rotation(String part,float x,float y,float z,boolean raw) { }
    private static Rotation d(String name,double x,double y,double z){return new Rotation(name,(float)(x*Math.PI/180),(float)(y*Math.PI/180),(float)(z*Math.PI/180),false);}
    private static Rotation raw(String name,double x,double y,double z){var r=d(name,x,y,z);return new Rotation(r.part,r.x,r.y,r.z,true);}
    private static final Rotation[] heavyPunchPose1={raw("head",0,385,0),d("body",7.5F,382.5F,0),d("upperPart",0,0,0),d("leftArm",0,0,-22.5F),d("leftForeArm",0,0,10),d("rightArm",0,0,80),d("rightForeArm",-70,-22.5F,-30),d("leftLeg",7.5F,0,-7.5F),d("leftLowerLeg",0,0,0),d("rightLeg",-7.5F,0,5),d("rightLowerLeg",17.5F,0,0)};
    private static final Rotation[] heavyPunchPose2={raw("head",10,400.5F,0),d("body",30,390,0),d("upperPart",0,7.5F,0),d("leftArm",7.5F,0,-45),d("leftForeArm",0,0,17.5F),d("rightArm",15,-5,100),d("rightForeArm",-90,-10,-70),d("leftLeg",15,0,-7.5F),d("leftLowerLeg",0,0,0),d("rightLeg",-12.5F,0,10),d("rightLowerLeg",25,0,0)};
    private static final Rotation[] heavyPunchPose3={raw("head",0,345,0),d("body",-7.5F,337.5F,-7.5F),d("upperPart",0,7.5F,0),d("leftArm",-22.5F,-15,-90),d("leftForeArm",-45,-15,75),d("rightArm",0,0,75),d("rightForeArm",0,90,0),d("leftLeg",20,0,-5),d("leftLowerLeg",0,0,0),d("rightLeg",1.6667F,-1.6667F,34.1667F),d("rightLowerLeg",26.67F,0,0)};
    private static final Rotation[] heavyPunchPose4={raw("head",-5,322.5F,0),d("body",-21.25F,282.5F,-13.75F),d("upperPart",0,7.5F,0),d("leftArm",-5.62F,-15,-90),d("leftForeArm",-45,-15,75),d("rightArm",30,0,75),d("rightForeArm",0,90,0),d("leftLeg",25,0,-2.5F),d("leftLowerLeg",0,0,0),d("rightLeg",15.8334F,-3.3334F,58.3334F),d("rightLowerLeg",28.34F,0,0)};
    private static final Rotation[] heavyPunchPose5={raw("head",-17.5F,300,-22.5F),d("body",-30,225,-15),d("upperPart",0,7.5F,0),d("leftArm",11.25F,-15,-90),d("leftForeArm",-45,-15,75),d("rightArm",60,0,75),d("rightForeArm",-20,60,-20),d("leftLeg",30,0,0),d("leftLowerLeg",0,0,0),d("rightLeg",30,-5,82.5F),d("rightLowerLeg",30,0,0)};
    private static final Rotation[] heavyPunchPose6={raw("head",15,60,0),d("body",15,102.5F,0),d("upperPart",0,7.5F,0),d("leftArm",45,-15,-90),d("leftForeArm",-45,-15,75),d("rightArm",60,0,75),d("rightForeArm",-60,0,-60),d("leftLeg",-3.3333F,6.6667F,-6.6667F),d("leftLowerLeg",45,0,0),d("rightLeg",-49.1667F,10.8333F,36.6667F),d("rightLowerLeg",80,0,0)};
    private static final Rotation[] heavyPunchPose7={d("head",0,15,-7.5F),d("body",22.5F,36.25F,-7.5F),d("upperPart",0,7.5F,0),d("leftArm",22.5F,-15,-90),d("leftForeArm",-67.5F,0,86.25F),d("rightArm",45,0,75),d("rightForeArm",-45,-5,-120),d("leftLeg",-20,10,-10),d("leftLowerLeg",67.5F,0,0),d("rightLeg",-88.75F,18.75F,13.75F),d("rightLowerLeg",105,0,0)};
    private static final Rotation[] heavyPunchPose8={d("head",0,0,0),d("body",30,-30,-15),d("upperPart",0,7.5F,0),d("leftArm",22.5F,15,-60),d("leftForeArm",-90,15,97.5F),d("rightArm",-45,-10,75),d("rightForeArm",-165.665F,-4.4638F,-133.0616F),d("leftLeg",-76.5527F,15.4535F,-3.9853F),d("leftLowerLeg",90,0,0),d("rightLeg",15,30,0),d("rightLowerLeg",30,0,0)};
    private static final Rotation[] heavyFinisherPose1={d("head",5,0,6.36F),d("body",4.2341F,39.7845F,6.5861F),d("upperPart",0F,5F,0F),d("leftArm",15F,-10F,-52.5F),d("leftForeArm",-88.6703F,-3.8472F,87.0901F),d("rightArm",10.1762F,16.6443F,93.1445F),d("rightForeArm",-77.4892F,-4.7192F,-74.0538F),d("leftLeg",-52.5F,-37.5F,0),d("leftLowerLeg",97.447F,-7.3536F,-2.2681F),d("rightLeg",8.2781F,-2.4033F,-0.0432F),d("rightLowerLeg",10,-5,0)};
    private static final Rotation[] heavyFinisherPose2={d("head",-6.9176F,15.7939F,16.6495F),d("body",16.7396F,58.5251F,19.4254F),d("upperPart",0F,15F,0F),d("leftArm",-11.0864F,-27.2098F,-49.134F),d("leftForeArm",-98.9572F,-21.4891F,114.4737F),d("rightArm",37.9264F,14.6364F,103.3191F),d("rightForeArm",-89.3397F,-34.9867F,-92.8194F),d("leftLeg",-36.5212F,-38.7805F,-7.0481F),d("leftLowerLeg",111.7619F,4.0651F,10.1255F),d("rightLeg",24.8305F,-0.7714F,0),d("rightLowerLeg",0.7594F,-5,0)};
    private static final Rotation[] heavyFinisherPose3={d("rightArm",-38.3F,20.47F,63.55F),d("rightForeArm",-67.5782F,1.503F,-72.9104F)};
    private static final Rotation[] heavyFinisherPose4={d("body",0,-30,0),d("upperPart",0F,-15F,0F),d("leftArm",17.8981F,11.9128F,-21.186F),d("leftForeArm",-83.3352F,3.9942F,28.1685F),d("rightArm",-72.6819F,35.6647F,53.5229F),d("rightForeArm",0,0,-12.5F),d("leftLeg",-48.4357F,19.6329F,0.1075F),d("leftLowerLeg",71.8824F,15.9537F,11.2591F),d("rightLeg",33.6661F,23.9013F,7.2025F),d("rightLowerLeg",22.5F,-5,0)};
    private static final Rotation[] itemFixRotations={d("head",31.301F,27.0408F,3.6059F),d("body",5.7686F,29.8742F,5.3807F),d("upperPart",0.0F,6.0F,0.0F),d("leftArm",-33.6218F,25.82F,-22.9983F),d("leftForeArm",-53.621F,-34.2195F,50.6576F),d("rightArm",-45.3923F,-27.0377F,10.4828F),d("rightForeArm",-38.0639F,-35.8085F,4.6156F)};
    private static final Rotation[] blockBulletRotations={d("body",30.7167F,25.4083F,17.1091F),d("upperPart",0.0F,-4.0F,0.0F),d("leftArm",-88.9066F,18.1241F,-39.2738F),d("leftForeArm",-45.6386F,-43.0305F,61.5635F),d("rightArm",-65.0702F,-23.5085F,5.5623F),d("rightForeArm",-97.8419F,36.1268F,-102.0079F),d("leftLeg",-50.8435F,-8.788F,-8.0132F),d("leftLowerLeg",97.5F,10,0),d("rightLeg",7.76F,-2.1895F,-3.2001F),d("rightLowerLeg",10,-5,0)};
    private static final Rotation[] IDLE={d("body",5.7686F,29.8742F,5.3807F),d("upperPart",0,6,0),d("leftArm",3.25F,-6.25F,-42.5F),d("leftForeArm",-75,-15,92.5F),d("rightArm",35,-15,40),d("rightForeArm",-85,-5,-20),d("leftLeg",-52.5F,-15,0),d("leftLowerLeg",97.5F,10,0),d("rightLeg",7.9315F,-12.0964F,-4.5742F),d("rightLowerLeg",10,-5,0)};
    private static final Rotation[] IDLE2={d("leftArm",7.9708F,-6.7104F,-40.0269F),d("leftForeArm",-74.8671F,-9.523F,91.3614F),d("rightArm",40.9054F,-11.7546F,36.0897F),d("rightForeArm",-92.4423F,-9.9808F,-20.4419F)};
    private static final Rotation[] SUMMON0={d("head",-32.5F,52.5F,-5),d("body",-7.5F,37.5F,0),d("upperPart",0,12.5F,0),d("leftArm",25.1426F,18.0217F,-54.0834F),d("leftForeArm",-37.1572F,-2.0551F,44.1678F),d("rightArm",-60.3335F,-8.4521F,68.4237F),d("rightForeArm",-135,22.5F,-90),d("leftLeg",-10.1402F,10.7145F,3.1723F),d("leftLowerLeg",45,0,0),d("rightLeg",27.9546F,33.9337F,7.8335F),d("rightLowerLeg",29.6217F,4.9809F,-8.6822F)};
    private static final Rotation[] SUMMON1={d("head",0,0,0),d("body",15,0,-10),d("upperPart",0,-12.5F,0),d("leftArm",-49.8651F,9.3787F,-53.3701F),d("leftForeArm",-93.6597F,-5.1369F,84.6506F),d("rightArm",30.2141F,-5.5863F,54.7232F),d("rightForeArm",0,0,0),d("leftLeg",-20,0,-45),d("leftLowerLeg",30,0,37.5F),d("rightLeg",-74.6528F,12.0675F,3.284F),d("rightLowerLeg",100,0,0)};
    private static final Rotation[] SUMMON2={d("head",-4.3644F,30.3695F,-8.668F),d("body",-35,-25,7),d("upperPart",0,10,0),d("leftArm",12.5F,0,-20),d("leftForeArm",7.5F,0,-10),d("rightArm",37.5F,7.5F,-7.5F),d("rightForeArm",-7.5F,0,0),d("leftLeg",-43.5572F,57.6471F,-22.5553F),d("leftLowerLeg",77.4538F,4.8812F,-1.0848F),d("rightLeg",-65,60,20),d("rightLowerLeg",107.7531F,9.5327F,3.0351F)};
    private static final Rotation[] SUMMON3={d("head",0,75,0),d("body",-10,60,0),d("upperPart",0,0,0),d("leftArm",16.7363F,5.188F,-39.2363F),d("leftForeArm",-52.4165F,42.9971F,31.9928F),d("rightArm",-60,0,75),d("rightForeArm",-154.2444F,12.7F,-103.0794F),d("leftLeg",12.5F,0,0),d("leftLowerLeg",22.5F,0,0),d("rightLeg",-56.6544F,29.5657F,5.3615F),d("rightLowerLeg",112.5F,0,0)};
    private static final Rotation[] JAB1 = {d("body",0,0,0),d("upperPart",0,-15,0),d("leftArm",-7.5,0,-15),d("leftForeArm",-100,15,7.5),d("rightArm",22.5,0,22.5),d("rightForeArm",-105,0,-15)};
    private static final Rotation[] JAB2 = {d("body",0,-5,0),d("upperPart",0,-20,0),d("leftArm",30,0,-15),d("leftForeArm",-107.5,15,7.5),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[] JAB3 = {d("body",0,-12.5,0),d("upperPart",0,-17.5,0),d("leftArm",37.5,0,-15),d("leftForeArm",-115,15,7.5),d("rightArm",-81.9244,11.0311,70.2661),d("rightForeArm",0,0,0)};
    private static final Rotation[] JAB4 = {d("body",0,-3.75,0),d("upperPart",0,-3.75,0),d("leftArm",5.63,0,-20.62),d("leftForeArm",-103.75,3.75,13.13),d("rightArm",5.941,8.4211,69.059),d("rightForeArm",-75,0,0)};
    private static final Rotation[] BARRAGE={d("body",0,0,0),d("upperPart",0,-30,0),d("leftArm",22.5,0,-60),d("leftForeArm",-135,0,0),d("rightArm",-90,0,90),d("rightForeArm",0,0,0)};
    private static final Rotation[] BARRAGE_END={d("body",0,0,0),d("upperPart",0,0,0),d("leftArm",-33.75,0,-75),d("leftForeArm",-67.5,0,0),d("rightArm",-33.75,0,75),d("rightForeArm",-67.5,0,0)};
    private static final Rotation[] BARRAGE_RECOVERY={d("body",0,0,0),d("upperPart",0,0,0),d("leftArm",22.5,0,-22.5),d("leftForeArm",-75,7.5,22.5),d("rightArm",22.5,0,22.5),d("rightForeArm",-75,-7.5,-22.5)};
    private static void pose(CrazyDiamondModel model,Rotation[] pose,float amount,boolean mirror){
        for(var r:pose){String name=r.part;if(mirror)name=name.startsWith("right")?"left"+name.substring(5):name.startsWith("left")?"right"+name.substring(4):name;
            if(r.raw)model.rawRotation(name,r.x,mirror?-r.y:r.y,mirror?-r.z:r.z,amount);else model.rotation(name,r.x,mirror?-r.y:r.y,mirror?-r.z:r.z,amount);}
    }
    private static void pose(CrazyDiamondModel model,Rotation[] pose,float amount){pose(model,pose,amount,false);}
    private static void head(CrazyDiamondModel model,CrazyDiamondRenderer.State s,float amount){model.rotation("head",s.xRot*Mth.DEG_TO_RAD,s.yRot*Mth.DEG_TO_RAD,0,amount);model.part("head").zRot=0;}
    private static void sequence(CrazyDiamondModel model,float progress,float[] points,Rotation[]... poses){
        int i=0;while(i<points.length-2&&progress>=points[i+1])i++;
        float amount=progress==points[i]?points[i]:(progress-points[i])/(points[i+1]-points[i]);
        pose(model,poses[i],1);pose(model,poses[i+1],amount);
    }
    private static void copyOwner(CrazyDiamondModel model,CrazyDiamondRenderer.State s){
        if(s.ownerPose==null)return;model.root().getAllParts().forEach(p->p.resetPose());
        String[] names={"head","torso","leftArm","rightArm","leftLeg","rightLeg"};
        for(int i=0;i<names.length;i++)model.rawRotation(names[i],s.ownerPose[i*3],s.ownerPose[i*3+1],s.ownerPose[i*3+2],1);
    }
    public static void apply(CrazyDiamondModel model,CrazyDiamondRenderer.State s){
        float p=Mth.clamp(s.actionTicks/Math.max(1,s.actionDuration),0,1),pitch=s.xRot*Mth.DEG_TO_RAD;
        pose(model,IDLE,1);head(model,s,1);
        if(s.restoration==Mode.REPAIR||s.restoration==Mode.PREVIOUS_STATE&&!s.entityWorked){
            if((s.summonMode&1)!=0)copyOwner(model,s);else pose(model,itemFixRotations,1,s.mainArmLeft);return;
        }
        if(s.healBarrage){barrage(model,s.ageInTicks/4,pitch,false,0);return;}
        if(s.kind==Kind.NONE||s.kind==Kind.BLOOD_CUTTER){
            if(s.summonAnimation&&s.ageInTicks<=20&&s.summonMode==0){
                model.root().getAllParts().forEach(part->part.resetPose());
                pose(model,new Rotation[][]{SUMMON0,SUMMON1,SUMMON2,SUMMON3}[Math.floorMod(s.summonVariant,4)],1);
                float f=Mth.clamp((s.ageInTicks-15)/5,0,1);pose(model,IDLE,f);head(model,s,f);
            }else pose(model,IDLE2,((float)Math.sin(Math.PI*(s.idleTicks/40-.5))-1)/2);
            return;
        }
        switch(s.kind){
            case HEAVY->{
                if(s.actionPhase==1)sequence(model,p,new float[]{0,.2222F,.3333F,.4444F,.5555F,.7777F,.8888F,1},heavyPunchPose1,heavyPunchPose2,heavyPunchPose3,heavyPunchPose4,heavyPunchPose5,heavyPunchPose6,heavyPunchPose7,heavyPunchPose8);
                else if(s.actionPhase==3){sequence(model,p,new float[]{0,.5F,1},heavyPunchPose8,heavyPunchPose8,IDLE);if(p>=.5F)head(model,s,(p-.5F)*2);}
                else pose(model,heavyPunchPose8,1);
            }
            case FINISHER,ANCHOR_MAKE->{
                if(s.actionPhase==1){pose(model,heavyFinisherPose1,1);pose(model,heavyFinisherPose2,p);}
                else if(s.actionPhase==2)sequence(model,p,new float[]{0,.5F,1},heavyFinisherPose2,heavyFinisherPose3,heavyFinisherPose4);
                else{sequence(model,p,new float[]{0,.5F,1},heavyFinisherPose4,heavyFinisherPose4,IDLE);if(p>=.5F)head(model,s,(p-.5F)*2);}
            }
            case BLOCK_BULLET->{if((s.summonMode&1)!=0)copyOwner(model,s);else{pose(model,blockBulletRotations,1,!s.mainArmLeft);model.secondPitch("leftArm",Math.min(pitch,1.0467F));model.secondPitch("rightArm",Math.min(pitch,1.0467F));}}
            case BLOCK->{
                model.rotation("body",0,0,0,1);model.rotation("upperPart",0,0,0,1);
                pose(model,new Rotation[]{d("rightForeArm",-90,30,-90),d("leftForeArm",-90,-30,90)},1);
                float b=Mth.clamp(pitch,-Mth.PI/3,Mth.PI/3)/2;
                model.rotation("rightArm",-1.5708F+b,-b/2,-Math.abs(b)/2+.7854F,1);model.rotation("leftArm",-1.5708F+b,b/2,Math.abs(b)/2-.7854F,1);
            }
            case BARRAGE->{if(s.actionPhase==4)barrage(model,s.ageInTicks/4,pitch,false,0);else sequence(model,p,new float[]{0,.25F,.5F,1},BARRAGE_END,BARRAGE_RECOVERY,BARRAGE_RECOVERY,IDLE);}
            case LIGHT->jab(model,s,p,pitch);
            default->{}
        }
    }
    public static float barrageEasing(float p){return p<.5F?8*p*p*p:1-(2*p-1)*(2*p-1)*(2*p-1);}
    public static void barrage(CrazyDiamondModel model,float loop,float pitch,boolean left,float z){
        float p=loop-(float)Math.floor(loop);pose(model,BARRAGE,1,!left);pose(model,BARRAGE,barrageEasing(p),left);
        model.secondPitch("leftArm",pitch);model.secondPitch("rightArm",pitch);model.part(left?"leftArm":"rightArm").zRot+=barrageEasing(p)*z;
    }
    private static void jab(CrazyDiamondModel model,CrazyDiamondRenderer.State s,float p,float pitch){
        boolean mirror=s.leftPunch;float armPitch=0;pose(model,JAB1,1,mirror);
        if(s.actionPhase==1){if(p<.5F){pose(model,JAB2,p*2,mirror);armPitch=p*2;}else{pose(model,JAB2,1,mirror);float f=Mth.clamp((p-.5F)*4,0,1);pose(model,JAB3,f*f*f,mirror);armPitch=1;}}
        else if(s.actionPhase==2){pose(model,JAB3,1,mirror);armPitch=1;if(p>=.25F&&p<.5F){float f=(p-.25F)*4;pose(model,JAB4,f*f*f,mirror);armPitch=1-f*f*f;}
            else if(p>=.5F){pose(model,JAB4,1,mirror);pose(model,JAB1,(p-.5F)*2,!mirror);armPitch=0;}}
        else{pose(model,JAB1,1,!mirror);if(p>.75F)pose(model,IDLE,(p-.75F)*4);}
        model.secondPitch("leftArm",pitch*armPitch);model.secondPitch("rightArm",pitch*armPitch);
    }
    private CrazyDiamondAnimation(){}
}
