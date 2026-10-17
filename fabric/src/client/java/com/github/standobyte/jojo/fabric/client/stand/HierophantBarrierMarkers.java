package com.github.standobyte.jojo.fabric.client.stand;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.client.OriginalHudDraw;
import com.github.standobyte.jojo.fabric.client.StandView;
import com.github.standobyte.jojo.fabric.stand.hierophant.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Original256-block marker sizing/icon/frame, projected into the HUD to retain through-block visibility. */
public final class HierophantBarrierMarkers {
    private static final Identifier ICON=RipplesFabric.id("textures/action/hierophant_green_barrier.png");
    private static final Identifier FRAME=RipplesFabric.id("textures/gui/additional.png");
    public static List<Vec3> positions(Minecraft client){
        if(client.level==null||client.player==null||!(StandView.ownManifestation(client) instanceof HierophantGreenEntity stand))return List.of();
        var points=new ArrayList<Vec3>();
        for(var entity:client.level.entitiesForRendering())if(entity instanceof HGBarrierEntity barrier&&barrier.livingOwner()==stand)
            barrier.wasRippedAt().ifPresent(points::add);
        return List.copyOf(points);
    }
    public static void render(GuiGraphicsExtractor graphics,float partial){
        var client=Minecraft.getInstance();if(client.gameRenderer.gameRenderState().guiRenderState.isHudHidden||client.level==null||client.player==null)return;
        var camera=client.gameRenderer.mainCamera();var matrix=camera.getViewRotationProjectionMatrix(new Matrix4f());
        float pixelsPerWorldUnit=graphics.guiHeight()/(2*(float)Math.tan(Math.toRadians(camera.getFov())/2));
        for(Vec3 point:positions(client)){
            Vec3 diff=point.subtract(camera.position());double distance=diff.length();if(distance>256||distance<1E-5)continue;
            var projected=matrix.transform(new Vector4f((float)diff.x,(float)diff.y,(float)diff.z,1));if(projected.w<=0)continue;
            float x=(projected.x/projected.w+1)*graphics.guiWidth()/2,y=(1-projected.y/projected.w)*graphics.guiHeight()/2;
            float worldScale=Math.min((float)Math.pow(2,(16-Math.min(distance,32))/16)*((float)distance)/256,1)*.8F;
            float scale=worldScale*pixelsPerWorldUnit/projected.w;
            graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().scale(scale,scale);
            OriginalHudDraw.blit(graphics,ICON,-8,-28,0,0,16,16,16,0xFFFFFFFF);
            OriginalHudDraw.blit(graphics,FRAME,-16,-32,0,0,32,32,256,0xFF00B319);
            graphics.pose().popMatrix();
        }
    }
    private HierophantBarrierMarkers(){ }
}
