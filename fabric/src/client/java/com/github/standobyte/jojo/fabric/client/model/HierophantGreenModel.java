package com.github.standobyte.jojo.fabric.client.model;

import com.github.standobyte.jojo.fabric.client.stand.HierophantAnimation;
import com.github.standobyte.jojo.fabric.client.stand.HierophantGreenRenderer.State;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Literal original HierophantGreenModel cuboids, UVs and segmented pivots (GPL-3.0). */
public final class HierophantGreenModel extends EntityModel<State> implements net.minecraft.client.model.ArmedModel<State> {
    private final Map<String, ModelPart> bones = new HashMap<>();
    public HierophantGreenModel(ModelPart baked) {
        super(baked, RenderTypes::entityTranslucent);
        ModelPart root = baked.getChild("root"); bones.put("root", root);
        ModelPart head = root.getChild("head"); bones.put("head", head);
        ModelPart body = root.getChild("body"); bones.put("body", body);
        ModelPart upperPart = body.getChild("upperPart"); bones.put("upperPart", upperPart);
        ModelPart torso = upperPart.getChild("torso"); bones.put("torso", torso);
        ModelPart bone7 = torso.getChild("bone7"); bones.put("bone7", bone7);
        ModelPart bone8 = torso.getChild("bone8"); bones.put("bone8", bone8);
        ModelPart bone9 = torso.getChild("bone9"); bones.put("bone9", bone9);
        ModelPart bone10 = torso.getChild("bone10"); bones.put("bone10", bone10);
        ModelPart bone11 = torso.getChild("bone11"); bones.put("bone11", bone11);
        ModelPart bone12 = torso.getChild("bone12"); bones.put("bone12", bone12);
        ModelPart leftArm = upperPart.getChild("leftArm"); bones.put("leftArm", leftArm);
        ModelPart leftArmJoint = leftArm.getChild("leftArmJoint"); bones.put("leftArmJoint", leftArmJoint);
        ModelPart leftForeArm = leftArm.getChild("leftForeArm"); bones.put("leftForeArm", leftForeArm);
        ModelPart rightArm = upperPart.getChild("rightArm"); bones.put("rightArm", rightArm);
        ModelPart rightArmJoint = rightArm.getChild("rightArmJoint"); bones.put("rightArmJoint", rightArmJoint);
        ModelPart rightForeArm = rightArm.getChild("rightForeArm"); bones.put("rightForeArm", rightForeArm);
        ModelPart leftLeg = body.getChild("leftLeg"); bones.put("leftLeg", leftLeg);
        ModelPart leftLegJoint = leftLeg.getChild("leftLegJoint"); bones.put("leftLegJoint", leftLegJoint);
        ModelPart leftLowerLeg = leftLeg.getChild("leftLowerLeg"); bones.put("leftLowerLeg", leftLowerLeg);
        ModelPart rightLeg = body.getChild("rightLeg"); bones.put("rightLeg", rightLeg);
        ModelPart rightLegJoint = rightLeg.getChild("rightLegJoint"); bones.put("rightLegJoint", rightLegJoint);
        ModelPart rightLowerLeg = rightLeg.getChild("rightLowerLeg"); bones.put("rightLowerLeg", rightLowerLeg);
    }
    public ModelPart bone(String name) { return bones.get(name); }
    @Override public void translateToHand(State state, net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack pose) {
        String side=arm==net.minecraft.world.entity.HumanoidArm.LEFT?"left":"right";
        pose.translate(arm==net.minecraft.world.entity.HumanoidArm.LEFT?-.0625:.0625,0,0);
        for(String name:new String[]{"body","upperPart",side+"Arm",side+"ForeArm"}) bone(name).translateAndRotate(pose);
        var forearm=bone(side+"ForeArm"); pose.translate(-forearm.x/16F,-forearm.y/16F,-forearm.z/16F);
    }
    @Override public void setupAnim(State state) {
        super.setupAnim(state);
        bones.values().forEach(part -> { part.resetPose(); part.visible = true; });
        HierophantAnimation.apply(this, state);
        boolean body = !state.armsOnly && (state.parts & 1) != 0;
        bone("head").visible = body; bone("torso").visible = body;
        bone("leftLeg").visible = bone("rightLeg").visible = !state.armsOnly && (state.parts & 4) != 0;
        bone("leftArm").visible = (state.parts & 2) != 0 && (!state.armsOnly || state.leftArm);
        bone("rightArm").visible = (state.parts & 2) != 0 && (!state.armsOnly || state.rightArm);
    }
    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0F, 0F, 0F));
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(36, 0).mirror(false).addBox(-4.5F, -4.5F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(24, 0).mirror(false).addBox(-1.5F, -8.5F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).texOffs(24, 4).mirror(false).addBox(-1.5F, -2.1F, -4.5F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(56, 0).mirror(false).addBox(-1.0F, -6.0F, -4.3F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(0, 0).mirror(false).addBox(-3.0F, -4.0F, -4.15F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(0, 2).mirror(true).addBox(1.0F, -4.0F, -4.15F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -24.0F, 0.0F, 0F, 0F, 0F));
        var body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -24.0F, 0.0F, 0F, 0F, 0F));
        var upperPart = body.addOrReplaceChild("upperPart", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0F, 0F, 0F));
        var torso = upperPart.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).mirror(false).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(27, 80).mirror(false).addBox(-1.0F, 3.0F, -2.8F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(33, 80).mirror(false).addBox(-1.0F, 3.0F, 1.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(24, 82).mirror(false).addBox(-2.0F, -0.5F, -2.5F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)).texOffs(27, 91).mirror(false).addBox(-2.5F, 4.5F, -2.6F, 5.0F, 5.0F, 1.0F, new CubeDeformation(-0.4F)), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0F, 0F, 0F));
        var bone7 = torso.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(12, 81).mirror(false).addBox(-0.5F, -4.5F, -2.5F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 3.5F, 0.0F, 0.0F, 0.0F, -0.5236F));
        var bone8 = torso.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(42, 81).mirror(true).addBox(-0.5F, -4.5F, -2.5F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.5236F));
        var bone9 = torso.addOrReplaceChild("bone9", CubeListBuilder.create().texOffs(0, 80).mirror(false).addBox(-0.5F, -5.5F, -2.5F, 1.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 3.5F, 0.0F, 0.0F, 0.0F, -0.8727F));
        var bone10 = torso.addOrReplaceChild("bone10", CubeListBuilder.create().texOffs(54, 80).mirror(false).addBox(-0.5F, -5.5F, -2.5F, 1.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.8727F));
        var bone11 = torso.addOrReplaceChild("bone11", CubeListBuilder.create().texOffs(14, 91).mirror(false).addBox(-0.5F, -4.5F, -1.5F, 1.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 3.5F, -0.25F, 0.0F, 0.0F, -2.5307F));
        var bone12 = torso.addOrReplaceChild("bone12", CubeListBuilder.create().texOffs(42, 91).mirror(true).addBox(-0.5F, -4.5F, -1.5F, 1.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 3.5F, -0.25F, 0.0F, 0.0F, 2.5307F));
        var leftArm = upperPart.addOrReplaceChild("leftArm", CubeListBuilder.create().texOffs(32, 108).mirror(false).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -10.0F, 0.0F, 0F, 0F, 0F));
        var leftArmJoint = leftArm.addOrReplaceChild("leftArmJoint", CubeListBuilder.create().texOffs(32, 102).mirror(true).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0F, 0F, 0F));
        var leftForeArm = leftArm.addOrReplaceChild("leftForeArm", CubeListBuilder.create().texOffs(32, 118).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0F, 0F, 0F));
        var rightArm = upperPart.addOrReplaceChild("rightArm", CubeListBuilder.create().texOffs(0, 108).mirror(false).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -10.0F, 0.0F, 0F, 0F, 0F));
        var rightArmJoint = rightArm.addOrReplaceChild("rightArmJoint", CubeListBuilder.create().texOffs(0, 102).mirror(false).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0F, 0F, 0F));
        var rightForeArm = rightArm.addOrReplaceChild("rightForeArm", CubeListBuilder.create().texOffs(0, 118).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0F, 0F, 0F));
        var leftLeg = body.addOrReplaceChild("leftLeg", CubeListBuilder.create().texOffs(96, 108).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.9F, 12.0F, 0.0F, 0F, 0F, 0F));
        var leftLegJoint = leftLeg.addOrReplaceChild("leftLegJoint", CubeListBuilder.create().texOffs(96, 102).mirror(true).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0F, 0F, 0F));
        var leftLowerLeg = leftLeg.addOrReplaceChild("leftLowerLeg", CubeListBuilder.create().texOffs(96, 118).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0F, 0F, 0F));
        var rightLeg = body.addOrReplaceChild("rightLeg", CubeListBuilder.create().texOffs(64, 108).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.9F, 12.0F, 0.0F, 0F, 0F, 0F));
        var rightLegJoint = rightLeg.addOrReplaceChild("rightLegJoint", CubeListBuilder.create().texOffs(64, 102).mirror(false).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0F, 0F, 0F));
        var rightLowerLeg = rightLeg.addOrReplaceChild("rightLowerLeg", CubeListBuilder.create().texOffs(64, 118).mirror(false).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.001F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
