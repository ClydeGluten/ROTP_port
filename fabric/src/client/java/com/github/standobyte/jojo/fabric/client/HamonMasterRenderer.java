package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Original HamonMaster skin, sitting animation and separately textured cape cubes. */
public final class HamonMasterRenderer extends MobRenderer<HamonMasterEntity,AvatarRenderState,PlayerModel>{
    public HamonMasterRenderer(EntityRendererProvider.Context context){super(context,new MasterModel(),.5F);addLayer(new RobeLayer(this));}
    @Override public AvatarRenderState createRenderState(){return new AvatarRenderState();}
    @Override public Identifier getTextureLocation(AvatarRenderState state){return RipplesFabric.id("textures/entity/biped/hamon_master.png");}
    private static final class MasterModel extends PlayerModel{
        MasterModel(){super(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot(),false);}
        @Override public void setupAnim(AvatarRenderState state){super.setupAnim(state);HamonPlayerAnimation.applyClip(this,"meditation",35+state.ageInTicks*.5F);}
    }
    private static final class RobeLayer extends RenderLayer<AvatarRenderState,PlayerModel>{
        private final ModelPart robe=robe();
        RobeLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
        @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
            var body=getParentModel().body;robe.x=body.x;robe.y=body.y;robe.z=body.z;robe.xRot=body.xRot;robe.yRot=body.yRot;robe.zRot=body.zRot;
            collector.submitModelPart(robe,pose,RenderTypes.entityCutout(RipplesFabric.id("textures/entity/biped/hamon_master_extra.png")),light,OverlayTexture.NO_OVERLAY,null);
        }
        private static PartPose rotated(float x,float y,float z,float rx,float ry,float rz){return PartPose.offsetAndRotation(x,y,z,rx*Mth.DEG_TO_RAD,ry*Mth.DEG_TO_RAD,rz*Mth.DEG_TO_RAD);}
        private static ModelPart robe(){
            var mesh=new MeshDefinition();var body=mesh.getRoot().addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
            body.addOrReplaceChild("rightBinding",CubeListBuilder.create().texOffs(36,36).addBox(-2,-.8F,-2,4,3,4,new CubeDeformation(.225F)),rotated(-2,12,0,37.5939F,-.8553F,1.1903F));
            var right=body.addOrReplaceChild("rightCape",CubeListBuilder.create().texOffs(36,45).addBox(-2,0,-.35F,4,4,0).texOffs(44,40).addBox(-2,0,-5.35F,0,4,5),rotated(-2,12,2,65.0177F,-9.1032F,.8307F));
            right.addOrReplaceChild("lowRight",CubeListBuilder.create().texOffs(44,44).addBox(0,0,-5,0,7,5).texOffs(36,49).addBox(0,0,0,4,7,0),rotated(-2,4,-.35F,21.39F,0,0));
            body.addOrReplaceChild("leftBinding",CubeListBuilder.create().texOffs(4,36).addBox(-2,-.8F,-2,4,3,4,new CubeDeformation(.225F)),rotated(2,12,0,35.298F,2.2454F,-.3715F));
            var left=body.addOrReplaceChild("leftCape",CubeListBuilder.create().texOffs(2,40).addBox(2,0,-5.35F,0,4,5).texOffs(12,45).addBox(-2,0,-.35F,4,4,0),rotated(2,12,2,69.3726F,6.8027F,-.6495F));
            left.addOrReplaceChild("lowLeft",CubeListBuilder.create().texOffs(2,44).addBox(0,0,-5,0,7,5).texOffs(12,49).addBox(-4,0,0,4,7,0),rotated(2,4,-.35F,14.85F,0,0));
            return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("body");
        }
    }
}
