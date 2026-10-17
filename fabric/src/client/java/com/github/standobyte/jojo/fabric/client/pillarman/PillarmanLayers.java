package com.github.standobyte.jojo.fabric.client.pillarman;

import com.github.standobyte.jojo.fabric.PillarmanAttachments;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Source stone skin plus eight original blade boxes, for every humanoid renderer. WIND owns its cloak. */
public final class PillarmanLayers<S extends HumanoidRenderState,M extends HumanoidModel<S>> extends RenderLayer<S,M> {
    private record View(boolean stone,boolean blades,boolean spectator){}
    private static final Map<HumanoidRenderState,View> VIEWS=new WeakHashMap<>();
    public static final net.minecraft.resources.Identifier STONE=RipplesFabric.id("textures/entity/biped/pillarman_layer.png");
    private static final net.minecraft.resources.Identifier BLADES=RipplesFabric.id("textures/entity/layer/pillarman_blades.png");
    private final ModelPart right=blade(false),left=blade(true);
    public PillarmanLayers(RenderLayerParent<S,M> parent){super(parent);}
    public static void clear(){VIEWS.clear();}
    public static void extract(LivingEntity entity,HumanoidRenderState state){
        var progress=entity.getAttachedOrCreate(PillarmanAttachments.PROGRESS);
        VIEWS.put(state,new View(progress.pillarman()&&progress.stoneForm(),progress.pillarman()&&entity.getAttachedOrCreate(PillarmanAttachments.BLADES),entity.isSpectator()));
        if(progress.pillarman()&&progress.stoneForm()&&state instanceof AvatarRenderState avatar){avatar.showHat=avatar.showJacket=avatar.showLeftSleeve=avatar.showRightSleeve=avatar.showLeftPants=avatar.showRightPants=false;}
    }
    public static void hideOuter(PlayerModel model,AvatarRenderState state){
        var view=VIEWS.get(state);if(view!=null&&view.stone)model.hat.visible=model.jacket.visible=model.leftSleeve.visible=model.rightSleeve.visible=model.leftPants.visible=model.rightPants.visible=false;
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,S state,float yaw,float pitch){
        var view=VIEWS.get(state);if(view==null||view.spectator)return;
        if(view.stone&&!state.isInvisible)collector.submitModel(getParentModel(),state,pose,RenderTypes.entityTranslucent(STONE),light,OverlayTexture.NO_OVERLAY,-1,null,state.outlineColor);
        // Original blades ignore normal Invisibility; FULL_INVISIBILITY cancels the whole renderer.
        if(view.blades)for(var side:HumanoidArm.values()){
            pose.pushPose();getParentModel().translateToHand(state,side,pose);
            PillarmanPlayerAnimation.bendHand(side==HumanoidArm.LEFT?getParentModel().leftArm:getParentModel().rightArm,pose);
            collector.submitModelPart(side==HumanoidArm.LEFT?left:right,pose,RenderTypes.armorCutoutNoCullGlint(BLADES),0xF000F0,OverlayTexture.NO_OVERLAY,null,-1,state.outlineColor);
            pose.popPose();
        }
    }
    private static ModelPart blade(boolean left){
        var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("blade",CubeListBuilder.create()
                .texOffs(0,0).addBox(.2F,-2.8F,-1,1,3,5)
                .texOffs(0,8).addBox(.2F,-2.8F,-4,1,2,3)
                .texOffs(10,0).addBox(.2F,-2.8F,-6,1,1,2)
                .texOffs(6,11).addBox(.2F,-3.8F,-7,1,1,4),PartPose.offsetAndRotation(left?2.3F:-.9F,9,5.9F,0,3.1416F,0));
        return LayerDefinition.create(mesh,16,16).bakeRoot().getChild("blade");
    }
}
