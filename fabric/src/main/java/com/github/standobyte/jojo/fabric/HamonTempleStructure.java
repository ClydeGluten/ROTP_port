package com.github.standobyte.jojo.fabric;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.Optional;
/** Original height gate, terrain samples, offsets, random draws and unrotated rocks. */
public final class HamonTempleStructure extends Structure {
    public static final MapCodec<HamonTempleStructure> CODEC=simpleCodec(HamonTempleStructure::new);
    public static final StructureType<HamonTempleStructure> TYPE=Registry.register(BuiltInRegistries.STRUCTURE_TYPE,RipplesFabric.id("hamon_temple"),()->CODEC);
    public static final StructurePieceType PIECE=Registry.register(BuiltInRegistries.STRUCTURE_PIECE,RipplesFabric.id("hamon_temple"),Piece::new);
    public HamonTempleStructure(StructureSettings settings){super(settings);}
    @Override public StructureType<?> type(){return TYPE;}
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context){
        if(!HamonConfig.server().hamonTempleSpawn())return Optional.empty();
        int x=context.chunkPos().getMinBlockX()+7,z=context.chunkPos().getMinBlockZ()+7;
        if(height(context,x,z)<90)return Optional.empty();
        int min=Integer.MAX_VALUE;
        for(int dx=-24;dx<=24;dx+=8)for(int dz=-24;dz<=24;dz+=8)min=Math.min(min,Math.max(80,height(context,x+dx,z+dz)));
        BlockPos origin=new BlockPos(x,min-3,z);
        return Optional.of(new GenerationStub(origin,builder->addPieces(context.structureTemplateManager(),origin,builder,context.random())));
    }
    private static int height(GenerationContext context,int x,int z){return context.chunkGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());}
    public static void addPieces(StructureTemplateManager templates,BlockPos origin,StructurePiecesBuilder builder,RandomSource random){
        builder.addPiece(new Piece(templates,"building",origin.offset(-24,-3,-24),Rotation.NONE));
        for(Rotation rotation:Rotation.values()){
            builder.addPiece(new Piece(templates,"pathway",origin.offset(-1,-4,-1).offset(new BlockPos(-22,0,-1).rotate(rotation)),rotation));
            random.nextInt(Rotation.values().length); // The original draws a rotation, then overwrites it with NONE.
            builder.addPiece(new Piece(templates,"rock_"+(random.nextInt(8)+1),origin.offset(-4,-3,-4).offset(new BlockPos(-21,0,0).rotate(rotation)),Rotation.NONE));
        }
    }
    public static final class Piece extends TemplateStructurePiece {
        Piece(StructureTemplateManager manager,String name,BlockPos pos,Rotation rotation){
            super(PIECE,0,manager,RipplesFabric.id("hamon_temple/"+name),"jojo:hamon_temple/"+name,settings(rotation),pos);
        }
        Piece(StructurePieceSerializationContext context,CompoundTag tag){super(PIECE,tag,context.structureTemplateManager(),id->settings(Rotation.valueOf(tag.getStringOr("Rotation","NONE"))));}
        private static StructurePlaceSettings settings(Rotation rotation){return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);}
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag){super.addAdditionalSaveData(context,tag);tag.putString("Rotation",placeSettings.getRotation().name());}
        @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox bounds){}
    }
    public static void initialize(){}
}
