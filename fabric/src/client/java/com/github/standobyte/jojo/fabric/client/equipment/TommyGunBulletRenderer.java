package com.github.standobyte.jojo.fabric.client.equipment;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.equipment.TommyGunBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Source four-block, fullbright, 0.015-radius bullet trace with the original strip UVs. */
public final class TommyGunBulletRenderer extends EntityRenderer<TommyGunBulletEntity, TommyGunBulletRenderer.State> {
    public static final class State extends EntityRenderState { List<Vec3> trace=List.of(); Vec3 origin=Vec3.ZERO; }
    public TommyGunBulletRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(TommyGunBulletEntity entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.origin=entity.position(); state.trace=List.copyOf(entity.tracePos);
        if(state.trace.size()<2)state.trace=List.of(entity.position().subtract(entity.getDeltaMovement().normalize().scale(.0625)),entity.position());
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        var vertices=new ArrayList<float[]>(); double remaining=4;
        for(int i=state.trace.size()-1;i>0 && remaining>0;i--) {
            Vec3 end=state.trace.get(i), start=state.trace.get(i-1), backward=start.subtract(end);
            double length=backward.length(); if(length<1.0E-8)continue;
            if(i==1 && length<.0625) { backward=backward.normalize().scale(.0625); length=.0625; }
            double used=Math.min(remaining,length);
            start=end.add(backward.normalize().scale(used));
            float u1=(float)(remaining/4); remaining-=used; float u0=(float)(remaining/4);
            Vec3 direction=backward.normalize();
            Vec3 normal=direction.cross(new Vec3(0,1,0)); if(normal.lengthSqr()<.001)normal=direction.cross(new Vec3(1,0,0));
            Vec3 a=normal.normalize().scale(.015),b=direction.cross(a).normalize().scale(.015);
            Vec3[] corners={a.add(b),a.subtract(b),a.reverse().subtract(b),b.subtract(a)};
            start=start.subtract(state.origin); end=end.subtract(state.origin);
            for(int side=0;side<4;side++) {
                var c=corners[side];var d=corners[(side+1)%4];var n=c.add(d).normalize();
                add(vertices,end.add(c),u1,0,n);add(vertices,start.add(c),u0,0,n);
                add(vertices,start.add(d),u0,1F/64,n);add(vertices,end.add(d),u1,1F/64,n);
            }
            if(i==state.trace.size()-1) {
                var n=direction.reverse();
                add(vertices,end.add(corners[3]),0,1F/64,n);add(vertices,end.add(corners[2]),1F/64,1F/64,n);
                add(vertices,end.add(corners[1]),1F/64,2F/64,n);add(vertices,end.add(corners[0]),0,2F/64,n);
            }
        }
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(RipplesFabric.id("textures/entity/projectiles/bullet_trace.png")),(entry,buffer)->{
            for(var v:vertices)buffer.addVertex(entry,v[0],v[1],v[2]).setColor(-1).setUv(v[3],v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(entry,v[5],v[6],v[7]);
        });
    }
    private static void add(List<float[]> vertices,Vec3 p,float u,float v,Vec3 n) {
        vertices.add(new float[]{(float)p.x,(float)(p.y+.03125),(float)p.z,u,v,(float)n.x,(float)n.y,(float)n.z});
    }
}
