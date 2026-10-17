package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.pillarman.*;
import com.github.standobyte.jojo.fabric.client.TimeStopClient;
import com.github.standobyte.jojo.fabric.client.VampireBendGeometry;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Unchanged upstream Pillar emotes, sampled from authoritative action clocks with original modifiers. */
public final class PillarmanPlayerAnimation {
    private record Key(float tick,float value,String easing){}
    private record Clip(Map<String,List<Key>[]> parts,int begin,int end,int stop,int back,boolean loop){}
    public record Pose(String clip,float tick,float weight,boolean mirror,boolean headLook,boolean armLook){}
    private record Previous(Pose pose,float end,float duration){}
    private static final Map<String,Clip> CLIPS=new HashMap<>();
    private static final Map<AvatarRenderState,Pose> POSES=new WeakHashMap<>();
    private static final Map<Avatar,Previous> PREVIOUS=new WeakHashMap<>();
    private static final Map<ModelPart,Float> BENDS=new WeakHashMap<>();
    private static final String[] PARTS={"head","torso","rightArm","leftArm","rightLeg","leftLeg"};
    private static final String[] CHANNELS={"x","y","z","pitch","yaw","roll","bend"};
    public static void clear(){POSES.clear();PREVIOUS.clear();BENDS.clear();}
    public static void bendHand(ModelPart arm,com.mojang.blaze3d.vertex.PoseStack pose){
        float bend=BENDS.getOrDefault(arm,0F);if(bend!=0){pose.translate(0,.25,0);pose.rotate(com.mojang.math.Axis.XP, bend);pose.translate(0,-.25,0);}
    }
    public static Pose pose(AvatarRenderState state){return POSES.get(state);}
    public static void extract(Avatar entity,AvatarRenderState state,float partial){
        if(TimeStopClient.isFrozen(entity))partial=0;
        var base=entity.getAttachedOrCreate(PillarmanAttachments.ACTIONS);var heat=PillarHeatActions.state(entity);var wind=PillarWindActions.state(entity);var light=PillarLightActions.state(entity);
        String clip="";float tick=0,speed=1;boolean mirror=false,head=false,arms=false,passengerAllowed=false;
        if(base.punchTicks()>0){clip="pillar_man_punch";tick=base.punchTicks()-1+partial;speed=2;mirror=true;passengerAllowed=true;}
        else if(base.held()==PillarmanBaseActions.Held.EVASION.ordinal()){clip="evasion";tick=base.heldTicks()+partial;speed=2;}
        else if(base.held()==PillarmanBaseActions.Held.AGILITY.ordinal()){clip="unnatural_agility";tick=base.heldTicks()+partial;speed=1.75F;}
        if(PossessionClient.heldTicks(entity)>=0){clip="pillar_man_possession";tick=PossessionClient.heldTicks(entity)+partial;passengerAllowed=true;}
        if(wind.held()==PillarWindActions.Held.DIVINE){clip="divine_sandstorm";tick=wind.ticks()+partial;head=arms=true;}
        if(wind.held()==PillarWindActions.Held.RIFT){clip="atmospheric_rift";tick=wind.ticks()+partial;head=true;}
        switch(heat.phase()){
            case ERRATIC->{clip="erratic_blaze_king";tick=heat.ticks()+partial;head=true;}
            case GIANT->{clip="giant_cartwheel_prison";tick=heat.ticks()+partial;}
            case DETONATION->{clip="self_detonation";tick=heat.ticks()+partial;}
            default->{}
        }
        switch(light.phase()){
            case FLASH->{clip="light_flash";tick=light.ticks()+partial;mirror=head=passengerAllowed=true;}
            case DECOY->{clip="light_flash_decoy";tick=light.ticks()+partial;mirror=passengerAllowed=true;}
            case SLASH->{clip="blade_slash";tick=light.ticks()+partial;speed=1.5F;mirror=passengerAllowed=true;}
            case DASH_CHARGE->{clip="blade_dash";tick=Math.min(10,light.ticks()+partial);mirror=passengerAllowed=true;}
            case DASH->{clip="blade_dash";tick=11+light.ticks()+partial;mirror=passengerAllowed=true;}
            case BARRAGE->{clip="blade_barrage";tick=light.ticks()+partial;speed=3.25F;arms=true;}
            default->{}
        }
        if(entity.isPassenger()&&!passengerAllowed)clip="";
        if(!entity.getAttachedOrCreate(PillarmanAttachments.PROGRESS).pillarman()||!entity.isAlive())clip="";
        tick*=speed;mirror&=entity.getMainArm()==HumanoidArm.LEFT;
        float age=entity.tickCount+partial;var old=PREVIOUS.get(entity);Pose pose;
        if(!clip.isEmpty()){
            pose=new Pose(clip,tick,1,mirror,head,arms);float fade=clip.equals("pillar_man_punch")?20:clip.equals("blade_slash")?15:10;
            PREVIOUS.put(entity,new Previous(pose,-1,fade));
        }else if(old!=null){
            float end=old.end<0?age:old.end,elapsed=age-end;
            if(elapsed<old.duration){var p=old.pose;pose=new Pose(p.clip,p.tick,(float)Math.pow(1-elapsed/old.duration,3),p.mirror,p.headLook,p.armLook);PREVIOUS.put(entity,new Previous(old.pose,end,old.duration));}
            else{PREVIOUS.remove(entity);pose=new Pose("",0,0,false,false,false);}
        }else pose=new Pose("",0,0,false,false,false);
        POSES.put(state,pose);
    }
    public static void apply(PlayerModel model,AvatarRenderState state){
        BENDS.remove(model.rightArm);BENDS.remove(model.leftArm);
        var pose=POSES.get(state);if(pose==null||pose.clip.isEmpty())return;
        Clip clip=CLIPS.computeIfAbsent(pose.clip,PillarmanPlayerAnimation::load);float tick=pose.tick;
        boolean looping=clip.loop&&tick>clip.end;
        if(looping)tick=clip.back+(tick-clip.back)%(clip.end-clip.back+1);
        else tick=Math.min(tick,clip.end);
        ModelPart[] parts={model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        ModelPart[] outer={model.hat,model.jacket,model.rightSleeve,model.leftSleeve,model.rightPants,model.leftPants};
        for(int i=0;i<parts.length;i++){
            int source=pose.mirror&&i>=2?(i%2==0?i+1:i-1):i;var channels=clip.parts.get(PARTS[source]);if(channels==null)continue;
            var p=parts[i];float[] original={p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,0};float[] values=new float[7];
            for(int c=0;c<7;c++){
                float fallback=original[c];boolean flip=pose.mirror&&(c==0||c==4||c==5);
                float value=sample(channels[c],tick,flip?-fallback:fallback,clip,looping);
                values[c]=flip?-value:value;
                if(i==0&&pose.headLook)values[c]+=original[c];
            }
            if((i==2||i==3)&&pose.armLook){
                var q=new org.joml.Quaternionf().rotationX(state.xRot*Mth.DEG_TO_RAD).mul(new org.joml.Quaternionf().rotationZYX(values[5],values[4],values[3]));
                var angles=q.getEulerAnglesZYX(new org.joml.Vector3f());values[3]=angles.x;values[4]=angles.y;values[5]=angles.z;
            }
            p.x=Mth.lerp(pose.weight,original[0],values[0]);p.y=Mth.lerp(pose.weight,original[1],values[1]);p.z=Mth.lerp(pose.weight,original[2],values[2]);
            p.xRot=Mth.lerp(pose.weight,original[3],values[3]);p.yRot=Mth.lerp(pose.weight,original[4],values[4]);p.zRot=Mth.lerp(pose.weight,original[5],values[5]);
            outer[i].loadPose(p.storePose());
            if(i==2||i==3)BENDS.put(p,values[6]*pose.weight);
            if(i>0){float pivot=i==2||i==3?4:6;VampireBendGeometry.set(p,pivot,values[6]*pose.weight,i==1);VampireBendGeometry.set(outer[i],pivot,values[6]*pose.weight,i==1);}
        }
    }
    @SuppressWarnings("unchecked") private static Clip load(String id){
        Map<String,List<Key>[]> parts=new HashMap<>();
        try(var in=PillarmanPlayerAnimation.class.getResourceAsStream("/assets/jojo/player_animation/pillarman/"+id+".json")){
            if(in==null)throw new IllegalStateException("Missing original Pillar animation "+id);
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("emote");
            boolean degrees=root.has("degrees")&&root.get("degrees").getAsBoolean();
            for(var value:root.getAsJsonArray("moves")){
                var move=value.getAsJsonObject();float tick=move.get("tick").getAsFloat();String ease=move.has("easing")?move.get("easing").getAsString():"LINEAR";
                for(String part:PARTS)if(move.has(part)){
                    var channels=parts.computeIfAbsent(part,k->{List<Key>[] c=new List[7];for(int n=0;n<7;n++)c[n]=new ArrayList<>();return c;});var data=move.getAsJsonObject(part);
                    for(int c=0;c<7;c++)if(data.has(CHANNELS[c]))channels[c].add(new Key(tick,data.get(CHANNELS[c]).getAsFloat()*(degrees&&c>=3?Mth.DEG_TO_RAD:1),ease));
                }
            }
            for(var channels:parts.values())for(var channel:channels)channel.sort(Comparator.comparingDouble(Key::tick));
            return new Clip(parts,root.get("beginTick").getAsInt(),root.get("endTick").getAsInt(),root.get("stopTick").getAsInt(),root.get("returnTick").getAsInt(),root.get("isLoop").getAsBoolean());
        }catch(IOException e){throw new IllegalStateException("Cannot load Pillar animation "+id,e);}
    }
    private static float sample(List<Key> keys,float tick,float fallback,Clip clip,boolean looping){
        if(keys.isEmpty())return fallback;
        Key before=new Key(clip.begin,fallback,"LINEAR"),after=null;
        for(var key:keys){if(key.tick<=tick)before=key;else{after=key;break;}}
        if(looping&&before.tick<clip.back)before=keys.getLast();
        if(after==null){
            if(!clip.loop)return before.value;
            after=keys.stream().filter(k->k.tick>=clip.back).findFirst().orElse(keys.getFirst());
        }
        float start=before.tick,end=after.tick;if(start>=end){if(tick<start)start-=clip.end-clip.back+1;else end+=clip.end-clip.back+1;}
        float t=Math.clamp((tick-start)/Math.max(.001F,end-start),0,1);
        // Source assets use LINEAR or EASEINOUTQUAD; KosmX defaults to the preceding key's easing.
        if(before.easing.equals("EASEINOUTQUAD"))t=t<.5F?2*t*t:1-(float)Math.pow(-2*t+2,2)/2;
        return Mth.lerp(t,before.value,after.value);
    }
    private PillarmanPlayerAnimation(){}
}
