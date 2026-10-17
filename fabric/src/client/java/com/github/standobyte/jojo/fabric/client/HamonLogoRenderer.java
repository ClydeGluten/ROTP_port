package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Original custom logo mesh, including the original non-axis-aligned bones and per-face UVs. */
public final class HamonLogoRenderer implements NoDataSpecialModelRenderer {
    private final float[][] vertices;
    public HamonLogoRenderer() {
        try(var input=HamonLogoRenderer.class.getResourceAsStream("/assets/jojo/geometry/mod_logo_mesh.json")) {
            if(input==null)throw new IllegalStateException("Missing original JoJo logo mesh");
            var array=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("vertices");
            vertices=new float[array.size()][8];
            for(int i=0;i<array.size();i++)for(int j=0;j<8;j++)vertices[i][j]=array.get(i).getAsJsonArray().get(j).getAsFloat();
        } catch(java.io.IOException error) {throw new IllegalStateException("Cannot load original JoJo logo mesh",error);}
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(RipplesFabric.id("textures/mod_logo_model.png")),(p,buffer)->{
            for(var v:vertices)buffer.addVertex(p,v[0],v[1],v[2]).setColor(255,255,255,255).setUv(v[3],v[4])
                    .setOverlay(overlay).setLight(light).setNormal(p,v[5],v[6],v[7]);
        });
    }
    @Override public void getExtents(Consumer<Vector3fc> consumer) {for(var v:vertices)consumer.accept(new Vector3f(v[0],v[1],v[2]));}
    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC=MapCodec.unit(Unbaked::new);
        @Override public MapCodec<Unbaked> type() {return MAP_CODEC;}
        @Override public HamonLogoRenderer bake(SpecialModelRenderer.BakingContext context) {return new HamonLogoRenderer();}
    }
}
