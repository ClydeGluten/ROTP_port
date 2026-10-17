package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Original terrain sampling, shared rotation and four-piece underground temple. */
public final class PillarTempleStructure extends Structure {
    public static final MapCodec<PillarTempleStructure> CODEC = simpleCodec(PillarTempleStructure::new);
    public static final StructureType<PillarTempleStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, RipplesFabric.id("pillarman_temple"), () -> CODEC);
    public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, RipplesFabric.id("pillarman_temple"), Piece::new);
    public PillarTempleStructure(StructureSettings settings) { super(settings); }
    @Override public StructureType<?> type() { return TYPE; }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!PillarWorldContent.templeSpawnEnabled()) return Optional.empty();
        int x = context.chunkPos().getMinBlockX() + 7, z = context.chunkPos().getMinBlockZ() + 7, min = Integer.MAX_VALUE;
        for (int dx = -26; dx < 30; dx += 8) for (int dz = -26; dz < 30; dz += 8)
            min = Math.min(min, context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
        BlockPos origin = new BlockPos(x, min - 3, z);
        return Optional.of(new GenerationStub(origin, builder -> addPieces(context.structureTemplateManager(), origin, builder, context.random())));
    }
    public static void addPieces(StructureTemplateManager manager, BlockPos origin, StructurePiecesBuilder builder, RandomSource random) {
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        builder.addPiece(new Piece(manager, "building", origin.offset(new BlockPos(-27, 0, -27).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, "stairway", origin.offset(new BlockPos(19, -42, -2).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, "corridor", origin.offset(new BlockPos(61, -43, -3).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, "bossroom", origin.offset(new BlockPos(102, -45, -16).rotate(rotation)), rotation));
    }
    public static final class Piece extends TemplateStructurePiece {
        Piece(StructureTemplateManager manager, String name, BlockPos pos, Rotation rotation) {
            super(PIECE, 0, manager, RipplesFabric.id("pillarman_temple/" + name), "jojo:pillarman_temple/" + name, settings(rotation), pos);
        }
        Piece(StructurePieceSerializationContext context, CompoundTag tag) { super(PIECE, tag, context.structureTemplateManager(), id -> settings(Rotation.valueOf(tag.getStringOr("Rotation", "NONE")))); }
        private static StructurePlaceSettings settings(Rotation rotation) { return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE); }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) { super.addAdditionalSaveData(context, tag); tag.putString("Rotation", placeSettings.getRotation().name()); }
        @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) { }
    }
    public static void initialize() { }
}
