package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.*;
import com.google.gson.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Samples the unchanged original KosmX emote assets, including sparse channels and easing. */
public final class HamonPlayerAnimation {
    private record Key(float tick,float value,String easing) { }
    private record Clip(Map<String,List<Key>[]> parts,float end,float returnTick,boolean loop) { }
    public record Pose(String clip,float tick,boolean protection,float age,float weight,boolean mirror) { }
    private record Previous(String clip,float tick,float ended,float duration,boolean mirror){}
    private static final Map<Avatar,Previous> PREVIOUS=new WeakHashMap<>();
    private static final String[] PARTS={"head","torso","rightArm","leftArm","rightLeg","leftLeg"};
    private static final String[] CHANNELS={"x","y","z","pitch","yaw","roll","bend"};
    private static final Map<String,Clip> CLIPS=new HashMap<>();
    private static final Map<AvatarRenderState,Pose> POSES=new WeakHashMap<>();
    public static Pose pose(AvatarRenderState state){return POSES.get(state);}
    public static void extract(Avatar entity,AvatarRenderState state,float partial){
        var action=HamonActionService.state(entity);var hud=HamonService.hud(entity);
        if(TimeStopClient.isFrozen(entity))partial=0;
        String id=action.continuous().isEmpty()?action.held():action.continuous();
        float tick=(action.continuous().isEmpty()?action.heldTicks():action.continuousTicks())+partial;
        String clip=switch(id){
            case "hamon_breath"->"breath_pose/breath_default";
            case "hamon_overdrive_beat"->"hamon_beat";
            case "hamon_shock"->"hamon_shock";
            case "hamon_sunlight_yellow_overdrive"->"sunlight_yellow_overdrive";
            case "jonathan_scarlet_overdrive"->"scarlet_overdrive";
            case "jonathan_overdrive_barrage"->"punch_barrage";
            case "jonathan_syo_barrage"->action.continuous().isEmpty()?"syo_barrage_start":action.continuousTicks()<70?"punch_barrage":"syo_barrage_finisher";
            case "joseph_rebuff_overdrive"->"rebuff_overdrive";
            case "zeppeli_sendo_wave_kick"->waveKickClip(entity);
            default->"";
        };
        if(id.equals("hamon_overdrive_beat"))tick*=2.25F;
        if(id.equals("hamon_shock"))tick*=2;
        if(id.equals("hamon_sunlight_yellow_overdrive")||id.equals("jonathan_scarlet_overdrive"))tick=action.continuous().isEmpty()?Math.min(80,tick):81+tick;
        if(id.equals("jonathan_syo_barrage")&&!action.continuous().isEmpty()&&action.continuousTicks()>=70)tick-=70;
        if(hud.meditating()){clip="meditation";tick=hud.meditationTicks()+partial;}
        var climbing=HamonWallAnimation.update(entity,partial);
        if(climbing!=null){clip=climbing.clip();tick=climbing.tick;}
        float age=entity.tickCount+partial,weight=1;
        boolean mirror=entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT&&(id.equals("hamon_overdrive_beat")||id.equals("hamon_sunlight_yellow_overdrive")||id.equals("jonathan_scarlet_overdrive"));
        if(climbing!=null)mirror=climbing.mirror;
        var previous=PREVIOUS.get(entity);
        if(clip.isEmpty()&&previous!=null){
            float ended=previous.ended()<0?age:previous.ended();
            float elapsed=age-ended;
            if(elapsed<previous.duration()){
                clip=previous.clip();tick=previous.tick();mirror=previous.mirror();weight=(float)Math.pow(1-elapsed/previous.duration(),3);
                PREVIOUS.put(entity,new Previous(clip,tick,ended,previous.duration(),mirror));
            }else PREVIOUS.remove(entity);
        }else if(!clip.isEmpty()){
            float duration=clip.equals("meditation")?4:clip.equals("hamon_shock")?16:clip.equals("hamon_beat")?22:10;
            PREVIOUS.put(entity,new Previous(clip,tick,-1,duration,mirror));
            if(id.equals("zeppeli_sendo_wave_kick"))weight=1-(float)Math.pow(1-Math.min(1,tick/4),3);
        }
        POSES.put(state,new Pose(clip,tick,action.protection(),age,weight,mirror));
    }
    private static String waveKickClip(Avatar entity){
        boolean mainLeft=entity.getMainArm()==HumanoidArm.LEFT;
        boolean left=!(mainLeft?entity.getMainHandItem():entity.getOffhandItem()).isEmpty();
        boolean right=!(mainLeft?entity.getOffhandItem():entity.getMainHandItem()).isEmpty();
        return "sendo_wave_kick"+(left?right?"_lr":"_l":right?"_r":"");
    }
    public static void apply(PlayerModel model,AvatarRenderState state){
        Pose pose=POSES.get(state);if(pose==null||pose.clip().isEmpty())return;
        applyClip(model,pose.clip(),pose.tick(),pose.weight(),pose.mirror());
    }
    public static void applyClip(PlayerModel model,String name,float tick){applyClip(model,name,tick,1,false);}
    private static void applyClip(PlayerModel model,String name,float tick,float weight,boolean mirror){
        Clip clip=CLIPS.computeIfAbsent(name,HamonPlayerAnimation::load);
        if(name.equals("punch_barrage")&&tick>4){float phase=(tick-2)%4;tick=phase<=2?2+phase:6-phase;}
        else if(clip.loop()&&tick>clip.end())tick=clip.returnTick()+(tick-clip.returnTick())%Math.max(1,clip.end()-clip.returnTick());
        else tick=Math.min(tick,clip.end());
        ModelPart[] parts={model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        ModelPart[] clothing={model.hat,model.jacket,model.rightSleeve,model.leftSleeve,model.rightPants,model.leftPants};
        for(int i=0;i<parts.length;i++){
            int source=mirror&&i>=2?i%2==0?i+1:i-1:i;
            var channels=clip.parts().get(PARTS[source]);if(channels==null)continue;
            var p=parts[i];float[] defaults={p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,0};float[] values=new float[7];
            for(int c=0;c<7;c++){
                float start=defaults[c];
                if(c<3&&mirror&&(c==0))start=-start;
                float value=sample(channels[c],tick,start);
                if(mirror&&(c==0||c==4||c==5))value=-value;
                values[c]=Mth.lerp(weight,defaults[c],value);
            }
            p.x=values[0];p.y=values[1];p.z=values[2];p.xRot=values[3];p.yRot=values[4];p.zRot=values[5];
            if(i>0){float pivot=i==2||i==3?4:6;VampireBendGeometry.set(p,pivot,values[6],i==1);VampireBendGeometry.set(clothing[i],pivot,values[6],i==1);}
        }
    }
    static net.minecraft.world.phys.Vec3 handTip(String clipName,float tick,boolean mirror,boolean left,float bodyYaw){
        Clip clip=CLIPS.computeIfAbsent(clipName,HamonPlayerAnimation::load);
        var channels=clip.parts().get((left^mirror)?"leftArm":"rightArm");
        float sign=mirror?-1:1;float[] v={left?5:-5,2,0,0,0,0,0};
        if(channels!=null)for(int i=0;i<7;i++){float value=sample(channels[i],tick,i==0?v[i]*sign:v[i]);v[i]=value*(mirror&&(i==0||i==4||i==5)?-1:1);}
        var offset=new net.minecraft.world.phys.Vec3(0,-.65,0);
        if(v[6]!=0)offset=offset.add(0,.225,0).xRot(-v[6]).add(0,-.225,0);
        offset=offset.add(left?.0625:-.0625,0,0);
        var position=new net.minecraft.world.phys.Vec3(v[0]/16,1.5-v[1]/16,v[2]/16).zRot(-v[5]).yRot(v[4]).xRot(v[3]);
        return position.add(offset).xRot(-v[3]).yRot(-v[4]).zRot(v[5]).yRot(-bodyYaw*Mth.DEG_TO_RAD).scale(.9375);
    }
    @SuppressWarnings("unchecked")
    private static Clip load(String id){
        Map<String,List<Key>[]> parts=new HashMap<>();
        try(var in=HamonPlayerAnimation.class.getResourceAsStream("/assets/jojo/player_animation/hamon/"+id+".json")){
            if(in==null)throw new IllegalStateException("Missing original Hamon animation "+id);
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("emote");
            boolean degrees=root.has("degrees")&&root.get("degrees").getAsBoolean();
            for(var value:root.getAsJsonArray("moves")){
                var move=value.getAsJsonObject();float tick=move.get("tick").getAsFloat();String easing=move.has("easing")?move.get("easing").getAsString():"LINEAR";
                for(String part:PARTS)if(move.has(part)){
                    var channels=parts.computeIfAbsent(part,key->{List<Key>[] c=new List[7];for(int i=0;i<7;i++)c[i]=new ArrayList<>();return c;});
                    var data=move.getAsJsonObject(part);
                    for(int i=0;i<7;i++)if(data.has(CHANNELS[i]))channels[i].add(new Key(tick,data.get(CHANNELS[i]).getAsFloat()*(degrees&&i>=3?Mth.DEG_TO_RAD:1),easing));
                }
            }
            for(var channels:parts.values())for(var channel:channels)channel.sort(Comparator.comparingDouble(Key::tick));
            return new Clip(parts,root.get("endTick").getAsFloat(),root.has("returnTick")?root.get("returnTick").getAsFloat():0,root.has("isLoop")&&root.get("isLoop").getAsBoolean());
        }catch(IOException error){throw new IllegalStateException("Cannot load Hamon animation "+id,error);}
    }
    private static float sample(List<Key> keys,float tick,float fallback){
        if(keys.isEmpty())return fallback;
        Key before=new Key(0,fallback,"LINEAR");
        for(var after:keys){if(tick<=after.tick()){
            float t=Math.clamp((tick-before.tick())/Math.max(.001F,after.tick()-before.tick()),0,1);
            return Mth.lerp(ease(t,after.easing()),before.value(),after.value());
        }before=after;}
        return before.value();
    }
    private static float ease(float t,String easing){return switch(easing){
        case "CONSTANT"->0;
        case "EASEINSINE"->1-(float)Math.cos(t*Math.PI/2);
        case "EASEOUTSINE"->(float)Math.sin(t*Math.PI/2);
        case "EASEINOUTSINE"->-(float)(Math.cos(Math.PI*t)-1)/2;
        case "EASEINQUAD"->t*t;
        case "EASEOUTQUAD"->1-(1-t)*(1-t);
        case "EASEINOUTQUAD"->t<.5F?2*t*t:1-(float)Math.pow(-2*t+2,2)/2;
        case "EASEINCUBIC"->t*t*t;
        case "EASEOUTCUBIC"->1-(float)Math.pow(1-t,3);
        case "EASEINOUTCUBIC"->t<.5F?4*t*t*t:1-(float)Math.pow(-2*t+2,3)/2;
        case "EASEINQUART"->t*t*t*t;
        case "EASEOUTQUART"->1-(float)Math.pow(1-t,4);
        case "EASEINOUTQUART"->t<.5F?8*t*t*t*t:1-(float)Math.pow(-2*t+2,4)/2;
        default->t;
    };}
    private HamonPlayerAnimation(){}
}
