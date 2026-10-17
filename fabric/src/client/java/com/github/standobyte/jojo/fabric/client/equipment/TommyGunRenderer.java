package com.github.standobyte.jojo.fabric.client.equipment;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.equipment.TommyGunItem;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Original textured mesh, melee flip, and fullbright two-frame muzzle flash. */
public final class TommyGunRenderer implements SpecialModelRenderer<TommyGunRenderer.State> {
    public record State(float fire, boolean swing, boolean firstPerson, boolean left) {}
    private record Context(ItemOwner owner, ItemDisplayContext display) {}
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private final float[][] body, fire;
    public static void begin(ItemOwner owner, ItemDisplayContext display) { CONTEXT.set(new Context(owner,display)); }
    public static void end() { CONTEXT.remove(); }
    public TommyGunRenderer() {
        try (var input=TommyGunRenderer.class.getResourceAsStream("/assets/jojo/geometry/tommy_gun_mesh.json")) {
            if(input==null) throw new IllegalStateException("Missing original Tommy gun mesh");
            var object=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            body=vertices(object.getAsJsonArray("body")); fire=vertices(object.getAsJsonArray("fire"));
        } catch(java.io.IOException error) { throw new IllegalStateException("Cannot load original Tommy gun mesh",error); }
    }
    private static float[][] vertices(com.google.gson.JsonArray array) {
        var result=new float[array.size()][8];
        for(int i=0;i<array.size();i++)for(int j=0;j<8;j++)result[i][j]=array.get(i).getAsJsonArray().get(j).getAsFloat();
        return result;
    }
    @Override public State extractArgument(ItemStack stack) {
        var context=CONTEXT.get(); var owner=context==null||context.owner()==null?null:context.owner().asLivingEntity();
        var display=context==null?ItemDisplayContext.NONE:context.display();
        boolean swing=owner!=null && owner.isSwinging() && owner.getItemInHand(owner.getCurrentSwing().hand())==stack;
        return new State(TommyGunItem.getGunshotTick(stack)-Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false),swing,display.firstPerson(),display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||display==ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        pose.pushPose();
        if(state!=null && state.swing()) {
            // Undo normal display then apply tommy_gun_flipped's original display transform.
            pose.translate(.5,.5,.5);
            if(!state.firstPerson()) pose.rotateDegrees(Axis.YP,state.left()?-15:15);
            pose.translate(0,state.firstPerson()?.25:.0625,state.firstPerson()?-.3125:-.75);
            pose.rotateDegrees(Axis.XP,-135); pose.translate(-.5,-.5,-.5);
        }
        submitMesh(body,"tommy_gun",pose,collector,light,overlay);
        if(state!=null && state.fire()>1) submitMesh(fire,state.fire()>=1.5?"tommy_gun_fire_2":"tommy_gun_fire_1",pose,collector,0xF000F0,overlay);
        pose.popPose();
    }
    private static void submitMesh(float[][] mesh,String texture,PoseStack pose,SubmitNodeCollector collector,int light,int overlay) {
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(RipplesFabric.id("textures/item/"+texture+".png")),(entry,buffer)->{
            for(var v:mesh)buffer.addVertex(entry,v[0],v[1],v[2]).setColor(-1).setUv(v[3],v[4]).setOverlay(overlay).setLight(light).setNormal(entry,v[5],v[6],v[7]);
        });
    }
    @Override public void getExtents(Consumer<Vector3fc> consumer) { for(var v:body)consumer.accept(new Vector3f(v[0],v[1],v[2])); }
    public record Unbaked() implements SpecialModelRenderer.Unbaked<State> {
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        @Override public SpecialModelRenderer<State> bake(SpecialModelRenderer.BakingContext context) { return new TommyGunRenderer(); }
        @Override public MapCodec<? extends SpecialModelRenderer.Unbaked<State>> type() { return CODEC; }
    }
}
