package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.model.generated.MagiciansRedGeometry;
import com.github.standobyte.jojo.fabric.client.stand.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Exact converted original MR hierarchy; body fire inherits these same limb transforms. */
public final class MagiciansRedModel extends EntityModel<MagiciansRedRenderer.State> {
    private final Map<String,ModelPart> parts=new HashMap<>();
    public MagiciansRedModel(){super(MagiciansRedGeometry.createRoot());for(String name:List.of("root","head","beakUpper","beakLower","body","upperPart","torso","leftArm","rightArm","leftForeArm","rightForeArm","leftLeg","rightLeg","leftLowerLeg","rightLowerLeg"))root().getAllParts().stream().filter(p->p.hasChild(name)).findFirst().ifPresent(p->parts.put(name,p.getChild(name)));}
    public ModelPart part(String name){return parts.get(name);}
    @Override public void setupAnim(MagiciansRedRenderer.State state){
        super.setupAnim(state);root().getAllParts().forEach(p->{p.resetPose();p.visible=true;p.skipDraw=false;});MagiciansRedAnimation.apply(this,state);
        if((state.summonMode&1)!=0){hideBody();boolean main=(state.summonMode&4)!=0,off=(state.summonMode&8)!=0;part("leftArm").visible=state.mainArmLeft?main:off;part("rightArm").visible=state.mainArmLeft?off:main;}
        if(state.firstPerson&&!MagiciansRedAnimation.flamePose(state.task))hideBody();
        if(!state.hasArms){part("leftArm").visible=false;part("rightArm").visible=false;}if(!state.hasLegs){part("leftLeg").visible=false;part("rightLeg").visible=false;}if(!state.hasBody){part("head").visible=false;part("torso").visible=false;}
    }
    private void hideBody(){for(String name:List.of("head","torso","leftLeg","rightLeg"))part(name).visible=false;for(String name:List.of("root","body","upperPart"))part(name).skipDraw=true;}
    public void rotation(String name,float x,float y,float z,float amount,boolean wrap){var p=part(name);if(p==null)return;p.xRot=lerp(p.xRot,x,amount,wrap);p.yRot=lerp(p.yRot,y,amount,wrap);p.zRot=lerp(p.zRot,z,amount,wrap);}
    private static float lerp(float from,float to,float amount,boolean wrap){float delta=to-from;if(wrap){float pi=(float)Math.PI;delta%=2*pi;if(delta>=pi)delta-=2*pi;if(delta< -pi)delta+=2*pi;}return from+delta*amount;}
    public void secondPitch(String name,float pitch){var p=part(name);if(p==null||pitch==0)return;var r=new Quaternionf().rotationX(pitch).mul(new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot)).getEulerAnglesZYX(new Vector3f());p.xRot=r.x;p.yRot=r.y;p.zRot=r.z;}
}
