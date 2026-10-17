package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.model.generated.CrazyDiamondGeometry;
import com.github.standobyte.jojo.fabric.client.stand.CrazyDiamondAnimation;
import com.github.standobyte.jojo.fabric.client.stand.CrazyDiamondRenderer.State;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;
import java.util.*;

/** Original Crazy Diamond cuboids and segmented limbs, generated directly from the authored source model. */
public final class CrazyDiamondModel extends EntityModel<State> implements net.minecraft.client.model.ArmedModel<State> {
    private final Map<String, ModelPart> parts = new HashMap<>();
    public CrazyDiamondModel() {
        super(CrazyDiamondGeometry.createRoot());
        for (String name : List.of("root", "head", "body", "upperPart", "torso", "leftArm", "rightArm", "leftForeArm", "rightForeArm",
                "leftLeg", "rightLeg", "leftLowerLeg", "rightLowerLeg"))
            root().getAllParts().stream().filter(part -> part.hasChild(name)).findFirst().ifPresent(parent -> parts.put(name, parent.getChild(name)));
    }
    @Override public void translateToHand(State state, net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack pose) {
        String side=arm==net.minecraft.world.entity.HumanoidArm.LEFT?"left":"right";
        pose.translate(arm==net.minecraft.world.entity.HumanoidArm.LEFT?-.0625:.0625,0,0);
        for(String name:new String[]{"body","upperPart",side+"Arm",side+"ForeArm"})part(name).translateAndRotate(pose);
        var forearm=part(side+"ForeArm");pose.translate(-forearm.x/16F,-forearm.y/16F,-forearm.z/16F);
    }
    public void rawRotation(String name,float x,float y,float z,float amount) {
        var p=part(name);p.xRot+=(x-p.xRot)*amount;p.yRot+=(y-p.yRot)*amount;p.zRot+=(z-p.zRot)*amount;
    }
    public ModelPart part(String name) { return parts.get(name); }
    @Override public void setupAnim(State state) {
        super.setupAnim(state); root().getAllParts().forEach(p -> { p.resetPose(); p.visible = true; p.skipDraw = false; });
        CrazyDiamondAnimation.apply(this, state);
        if ((state.summonMode & 1) != 0) {
            hideBody();
            boolean main = (state.summonMode & 4) != 0, off = (state.summonMode & 8) != 0;
            part("leftArm").visible = state.mainArmLeft ? main : off;
            part("rightArm").visible = state.mainArmLeft ? off : main;
        }
        if(state.firstPerson)hideBody();
        if(!state.hasArms){part("leftArm").visible=false;part("rightArm").visible=false;}
        if(!state.hasLegs){part("leftLeg").visible=false;part("rightLeg").visible=false;}
        if(!state.hasBody){part("head").visible=false;part("torso").visible=false;}
    }
    private void hideBody() {
        for (String name : List.of("head", "torso", "leftLeg", "rightLeg")) part(name).visible = false;
        for (String name : List.of("root", "body", "upperPart")) part(name).skipDraw = true;
    }
    public void onlyArm(boolean left) { hideBody(); part("leftArm").visible = left; part("rightArm").visible = !left; }
    public void rotation(String part, float x, float y, float z, float amount) {
        var model = parts.get(part); if (model == null) return;
        model.xRot = lerpAngle(model.xRot, x, amount); model.yRot = lerpAngle(model.yRot, y, amount); model.zRot = lerpAngle(model.zRot, z, amount);
    }
    private static float lerpAngle(float from, float to, float amount) {
        float pi=(float)java.lang.Math.PI,delta=(to-from)%(2*pi);if(delta>=pi)delta-=2*pi;if(delta < -pi)delta+=2*pi;return from+delta*amount;
    }
    /** Source ClientUtil.rotateAngles composes secondX before the authored ZYX rotation. */
    public void secondPitch(String part, float pitch) {
        var model = parts.get(part); if (model == null || pitch == 0) return;
        var rotation = new Quaternionf().rotationX(pitch).mul(new Quaternionf().rotationZYX(model.zRot, model.yRot, model.xRot));
        var angles = rotation.getEulerAnglesZYX(new Vector3f()); model.xRot = angles.x; model.yRot = angles.y; model.zRot = angles.z;
    }
}
