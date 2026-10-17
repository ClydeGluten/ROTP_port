package com.github.standobyte.jojo.fabric.client;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
/** Literal original Blockbench cubes, pivots, UVs and rotations. */
final class HamonContentModels {
    static ModelPart glider() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var glider=root.addOrReplaceChild("glider",CubeListBuilder.create().texOffs(0, 0).addBox(-20.0F, -0.5F, -4.11F, 40.0F, 1.0F, 24.0F,new CubeDeformation( -0.375F)),PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F,0,0,0));
        var frontLeft=glider.addOrReplaceChild("frontLeft",CubeListBuilder.create().texOffs(0, 25).addBox(-0.375F, -0.5F, -0.375F, 26.0F, 1.0F, 16.0F,new CubeDeformation( -0.375F)),PartPose.offsetAndRotation(0.0F, 0.0F, -19.625F,0.0F, -0.6806F, 0.0F));
        var frontRight=glider.addOrReplaceChild("frontRight",CubeListBuilder.create().texOffs(0, 42).addBox(-25.625F, -0.5F, -0.375F, 26.0F, 1.0F, 16.0F,new CubeDeformation( -0.375F)),PartPose.offsetAndRotation(0.0F, 0.0F, -19.625F,0.0F, 0.6806F, 0.0F));
        return LayerDefinition.create(mesh,16,16).bakeRoot().getChild("glider");
    }
    static ModelPart crimson() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var bubble=root.addOrReplaceChild("bubble",CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,new CubeDeformation( 0.0F)).texOffs(32, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F,new CubeDeformation( 0.0F)).texOffs(0, 16).addBox(-2.0F, -2.0F, -5.5F, 4.0F, 4.0F, 11.0F,new CubeDeformation( 0.0F)).texOffs(30, 16).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F,new CubeDeformation( 0.0F)).texOffs(30, 28).addBox(-5.5F, -2.0F, -2.0F, 11.0F, 4.0F, 4.0F,new CubeDeformation( 0.0F)).texOffs(0, 31).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 10.0F, 6.0F,new CubeDeformation( 0.0F)).texOffs(24, 36).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 11.0F, 4.0F,new CubeDeformation( 0.0F)),PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F,0,0,0));
        return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("bubble");
    }
    static ModelPart clackers() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var clackers=root.addOrReplaceChild("clackers",CubeListBuilder.create().texOffs(18, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F,new CubeDeformation( -0.25F)),PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F,0,0,0));
        var string1=clackers.addOrReplaceChild("string1",CubeListBuilder.create().texOffs(24, 6).addBox(-0.5F, -7.925F, -0.5F, 1.0F, 8.0F, 1.0F,new CubeDeformation( -0.075F)),PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F,0,0,0));
        var ball1=string1.addOrReplaceChild("ball1",CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.5F, -3.0F, 6.0F, 6.0F, 6.0F,new CubeDeformation( -1.5F)),PartPose.offsetAndRotation(0.0F, -7.85F, 0.0F,0,0,0));
        var string2=clackers.addOrReplaceChild("string2",CubeListBuilder.create().texOffs(28, 6).addBox(-0.5F, -0.075F, -0.5F, 1.0F, 8.0F, 1.0F,new CubeDeformation( -0.075F)),PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F,0,0,0));
        var ball2=string2.addOrReplaceChild("ball2",CubeListBuilder.create().texOffs(0, 12).addBox(-3.0F, -1.2F, -3.0F, 6.0F, 6.0F, 6.0F,new CubeDeformation( -1.5F)),PartPose.offsetAndRotation(0.0F, 7.55F, 0.0F,0,0,0));
        return LayerDefinition.create(mesh,32,32).bakeRoot().getChild("clackers");
    }
    private HamonContentModels(){}
}
