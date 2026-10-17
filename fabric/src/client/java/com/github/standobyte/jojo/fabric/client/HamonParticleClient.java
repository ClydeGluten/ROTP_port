package com.github.standobyte.jojo.fabric.client;
import com.github.standobyte.jojo.fabric.HamonParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Original HamonAuraParticle lifetime/alpha and Sendo's constant-speed face-constrained sparks. */
public final class HamonParticleClient {
    public static void initialize(){
        for(var aura:java.util.List.of(HamonParticles.AURA,HamonParticles.AURA_BLUE,HamonParticles.AURA_YELLOW,HamonParticles.AURA_RED,HamonParticles.AURA_SILVER)) ParticleProviderRegistry.getInstance().register(aura,sprites->(type,level,x,y,z,dx,dy,dz,random)->new Aura(level,x,y,z,dx,dy,dz,sprites));
        for(var spark:java.util.List.of(HamonParticles.SPARK_BLUE,HamonParticles.SPARK_YELLOW,HamonParticles.SPARK_RED,HamonParticles.SPARK_SILVER))ParticleProviderRegistry.getInstance().register(spark,sprites->(type,level,x,y,z,dx,dy,dz,random)->new VampireHamonParticleClient.Spark(level,x,y,z,dx,dy,dz,sprites.get(random)));
        for(var axis:Direction.Axis.values()){
            var type=switch(axis){case X->HamonParticles.SENDO_X;case Y->HamonParticles.SENDO_Y;case Z->HamonParticles.SENDO_Z;};
            ParticleProviderRegistry.getInstance().register(type,sprites->(particle,level,x,y,z,dx,dy,dz,random)->new Sendo(level,x,y,z,dx,dy,dz,sprites.get(random),axis));
        }
    }
    private static final class Aura extends SingleQuadParticle{
        private final SpriteSet sprites;private final int spriteOffset;
        Aura(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites){
            super(level,x,y,z,dx,dy,dz,sprites.get(level.getRandom()));this.sprites=sprites;
            xd=xd*.1+dx;yd=yd*.1+dy+.004;zd=zd*.1+dz;friction=.96F;hasPhysics=false;
            quadSize*=.3F;quadSize*=1.2F+.6F*random.nextFloat();lifetime=25+random.nextInt(10);spriteOffset=random.nextInt(lifetime);alpha=.25F;
        }
        @Override public void tick(){super.tick();float a=(float)age/lifetime;alpha=.05F+(a<=.5F?a*2:(1-a)*2)*.3F;setSprite(sprites.get((age+spriteOffset)%lifetime,lifetime));}
        @Override protected int getLightCoords(float partial){return 0xF000F0;}
        @Override protected Layer getLayer(){return Layer.TRANSLUCENT;}
    }
    private static final class Sendo extends SingleQuadParticle{
        private final Direction.Axis axis;
        Sendo(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,TextureAtlasSprite sprite,Direction.Axis axis){
            super(level,x,y,z,0,0,0,sprite);this.axis=axis;xd=dx;yd=dy;zd=dz;lifetime=15;hasPhysics=false;gravity=0;friction=1;quadSize*=.75F;
        }
        @Override public void tick(){super.tick();alpha=hasFace()?1:0;}
        private boolean hasFace(){
            BlockPos p=switch(axis){case X->new BlockPos((int)Math.round(x),(int)Math.floor(y),(int)Math.floor(z));case Y->new BlockPos((int)Math.floor(x),(int)Math.round(y),(int)Math.floor(z));case Z->new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.round(z));};
            Direction negative=Direction.fromAxisAndDirection(axis,Direction.AxisDirection.NEGATIVE);
            return face(p,negative)||face(p.relative(negative),negative.getOpposite());
        }
        private boolean face(BlockPos p,Direction direction){return !level.getBlockState(p).getCollisionShape(level,p).getFaceShape(direction).isEmpty();}
        @Override protected int getLightCoords(float partial){return 0xF000F0;}
        @Override protected Layer getLayer(){return Layer.OPAQUE;}
    }
    private HamonParticleClient(){}
}
