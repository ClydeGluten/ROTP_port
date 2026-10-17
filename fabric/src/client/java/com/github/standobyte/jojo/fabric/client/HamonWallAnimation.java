package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Direct port of KosmXWallClimbKeyframePlayer's half-cycle transitions and alternating hand contacts. */
final class HamonWallAnimation {
    enum Direction {UP(false),DOWN(false),LEFT(true),RIGHT(true);final boolean horizontal;Direction(boolean horizontal){this.horizontal=horizontal;}}
    static final class Track {
        Direction direction=Direction.UP,next;
        float tick,age=-1,speed=.25F;
        boolean moving,consecutive=true,stopped,mirror,leftTouch,rightTouch;
        int beforeChange,lastTouch,lastEntityTick=-1;Vec3 lastPosition,motion=Vec3.ZERO;
        void update(Avatar entity,float currentAge,float yaw){
            if(lastEntityTick!=entity.tickCount){motion=entity==Minecraft.getInstance().player?entity.getDeltaMovement():lastPosition==null?entity.getDeltaMovement():entity.position().subtract(lastPosition);lastPosition=entity.position();lastEntityTick=entity.tickCount;}
            var movement=motion;double up=movement.y,left=movement.yRot(-yaw*(float)Math.PI/180).x;
            Direction wanted=up>1E-7?Direction.UP:up< -1E-7?Direction.DOWN:left>1E-7?Direction.LEFT:left< -1E-7?Direction.RIGHT:null;
            moving=movement.lengthSqr()>1E-7&&wanted!=null;
            if(moving){if(stopped)direction(wanted);else next=wanted;var h=HamonService.state(entity);float climbSpeed=(float)((1.2+h.breathingLevel()*.004+h.controlLevel()*.00667)*HamonService.efficiency(entity,h.breathStability()/2,"wall_climbing")*entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                if(Math.abs(up)>1E-7&&Math.abs(left)>1E-7)climbSpeed*=up>0?1/Math.sqrt(1.25):.5/Math.sqrt(.5);
                speed=Math.clamp(climbSpeed/.06F,1,Math.abs(up)>1E-7?2.5F:1.25F);if(stopped)consecutive=true;stopped=false;}
            float delta=age<0?0:Math.clamp(currentAge-age,0,2);age=Math.max(age,currentAge);tick+=delta*speed;
            int end=direction.horizontal?13:25;if(tick>end)tick=1+(tick-1)%(end-1);
            boolean stop=(int)tick==0||!moving&&!stopped;
            if(next!=null||stop){int cycle=(int)tick%24;if(consecutive){beforeChange=tick>0?cycle:-1;consecutive=false;}
                if(beforeChange==-1&&cycle>=1||!(beforeChange<12 ^ cycle>=12)){
                    if(stop){speed=0;stopped=true;}
                    else if(next!=null){direction(next);next=null;consecutive=true;}
                }
            }
            if(!direction.horizontal){int touch=((int)tick+3)%24;
                if(!rightTouch&&lastTouch<12&&touch>=12){leftTouch=false;rightTouch=true;spark(entity,this,false);}
                else if(!leftTouch&&lastTouch>=12&&touch<12){leftTouch=true;rightTouch=false;spark(entity,this,true);}lastTouch=touch;
            }
        }
        private void direction(Direction value){if(direction==value)return;Direction old=direction;direction=value;
            if(!old.horizontal&&value.horizontal){mirror=tick<=12;tick%=12;}
            else if(old.horizontal&&!value.horizontal){tick=mirror?13:1;mirror=false;}}
        String clip(){return "wall_climb/wall_climb_"+direction.name().toLowerCase(Locale.ROOT);}
    }
    private static final Map<Avatar,Track> TRACKS=new WeakHashMap<>();
    static Track update(Avatar entity,float partial){var action=HamonActionService.state(entity);if(!action.wallClimbing()){TRACKS.remove(entity);return null;}var track=TRACKS.computeIfAbsent(entity,e->new Track());track.update(entity,entity.tickCount+(TimeStopClient.isFrozen(entity)?0:partial),action.wallYaw());return track;}
    static void tick(Minecraft client){if(client.player!=null)update(client.player,0);}
    private static void spark(Avatar entity,Track track,boolean left){
        var client=Minecraft.getInstance();boolean first=entity==client.player&&client.options.getCameraType().isFirstPerson();
        var position=first?entity.getEyePosition().add(new Vec3(left?.25:-.25,0,.25).yRot((180+entity.yBodyRot)*(float)Math.PI/180))
                :entity.position().add(HamonPlayerAnimation.handTip(track.clip(),track.tick,track.mirror,left,entity.yBodyRot));
        for(int i=0;i<4;i++)entity.level().addParticle(VampireHamonParticles.SPARK,position.x+entity.getRandom().nextGaussian()*.05,position.y+entity.getRandom().nextGaussian()*.05,position.z+entity.getRandom().nextGaussian()*.05,entity.getRandom().nextGaussian()*.25,entity.getRandom().nextGaussian()*.25,entity.getRandom().nextGaussian()*.25);
        if(client.level!=null)client.level.playLocalSound(position.x,position.y,position.z,JojoSounds.get("hamon_spark"),entity.getSoundSource(),.0625F,1+(entity.getRandom().nextFloat()-.5F)*.15F,false);
    }
    private HamonWallAnimation(){}
}
