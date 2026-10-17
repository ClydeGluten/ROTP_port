package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.model.generated.SilverChariotGeometry;
import com.github.standobyte.jojo.fabric.client.model.generated.SilverChariotArmorGeometry;
import com.github.standobyte.jojo.fabric.client.stand.SilverChariotAnimation;
import com.github.standobyte.jojo.fabric.client.stand.SilverChariotRenderer.State;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;
import java.util.*;

/** Literal independently generated source body/armor geometry; animation addresses original part names. */
public final class SilverChariotModel extends EntityModel<State> {
    private final Map<String, ModelPart> parts = new HashMap<>();
    public SilverChariotModel(boolean armor) {
        super(armor ? SilverChariotArmorGeometry.createRoot() : SilverChariotGeometry.createRoot());
        for (String name : List.of("root", "head", "body", "upperPart", "torso", "leftArm", "rightArm", "leftForeArm", "rightForeArm",
                "leftLeg", "rightLeg", "leftLowerLeg", "rightLowerLeg", "rapier", "rapierBlade"))
            root().getAllParts().stream().filter(part -> part.hasChild(name)).findFirst().ifPresent(parent -> parts.put(name, parent.getChild(name)));
    }
    public ModelPart part(String name) { return parts.get(name); }
    @Override public void setupAnim(State state) {
        super.setupAnim(state); root().getAllParts().forEach(p -> { p.resetPose(); p.visible = true; p.skipDraw = false; });
        SilverChariotAnimation.apply(this, state);
        var blade = part("rapierBlade"); if (blade != null) blade.visible = state.rapier;
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
