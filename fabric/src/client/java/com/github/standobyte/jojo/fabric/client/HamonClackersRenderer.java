package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import java.util.function.Consumer;
import org.joml.Vector3fc;

/** Original ClackersISTER third-person string motion; the source first-person method is empty. */
public final class HamonClackersRenderer implements SpecialModelRenderer<HamonClackersRenderer.Pose>{
    public record Pose(float angle,boolean using,boolean left,float swing){}
    private record Context(ItemOwner owner,ItemDisplayContext display){}
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    private final ModelPart model=HamonContentModels.clackers();
    public static void begin(ItemOwner owner,ItemDisplayContext display){CONTEXT.set(new Context(owner,display));}
    public static void end(){CONTEXT.remove();}
    @Override public Pose extractArgument(ItemStack stack){
        var context=CONTEXT.get();var owner=context==null||context.owner()==null?null:context.owner().asLivingEntity();
        boolean left=context!=null&&context.display()==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean using=owner!=null&&owner.isUsingItem()&&owner.getUseItem()==stack;
        float angle=0,swing=0;
        if(using){
            int ticks=owner.getTicksUsingItem();float loop;
            if(ticks>20){loop=1-(ticks%2+partial)/2;loop=1-loop*loop;if(ticks%4<2)loop=1-loop;angle=(float)Math.PI*((1-loop)*7/8+1F/16);}
            else{loop=(ticks%5+partial)/5;loop=loop<.5F?loop*2:2-loop*2;loop=1-loop;loop=1-loop*loop;float amplitude=(float)Math.PI*(1F/6+1F/12*(ticks/5)-1F/16);angle=amplitude*loop+(float)Math.PI/16;}
        }else if(owner!=null)swing=(float)Math.cos(owner.walkAnimation.position(partial)*.6664F+Math.PI)*Math.min(1,owner.walkAnimation.speed(partial))*(left?-1:1);
        return new Pose(angle,using,left,swing);
    }
    @Override public void submit(Pose state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){
        model.resetPose();model.y=-2;var first=model.getChild("string1");var second=model.getChild("string2");first.resetPose();second.resetPose();
        if(state!=null&&state.using()){first.zRot=state.angle();second.zRot=(float)Math.PI-state.angle();}
        else{float swing=state==null?0:state.swing();first.xRot=(float)Math.PI/2+(float)Math.PI/32+swing;second.xRot=-(float)Math.PI/2-(float)Math.PI/32+swing;first.yRot=(float)Math.PI/16;second.yRot=-(float)Math.PI/16;}
        collector.submitModelPart(model,pose,RenderTypes.entityCutout(RipplesFabric.id("textures/entity/projectiles/clackers.png")),light,OverlayTexture.NO_OVERLAY,null,-1,outline);
    }
    @Override public void getExtents(Consumer<Vector3fc> consumer){model.getExtentsForGui(new PoseStack(),consumer);}
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Pose>{
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        @Override public SpecialModelRenderer<Pose> bake(SpecialModelRenderer.BakingContext context){return new HamonClackersRenderer();}
        @Override public MapCodec<? extends SpecialModelRenderer.Unbaked<Pose>> type(){return CODEC;}
    }
}
