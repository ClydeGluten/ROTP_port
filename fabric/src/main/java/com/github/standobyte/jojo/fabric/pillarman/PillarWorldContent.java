package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.JojoSounds;
import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Shared registration contract for temple, sleeping sculpture and the HEAT fluid paths. */
public final class PillarWorldContent {
    public static final BoilingBloodFluid.Flowing FLOWING_BOILING_BLOOD = Registry.register(BuiltInRegistries.FLUID, RipplesFabric.id("flowing_boiling_blood"), new BoilingBloodFluid.Flowing());
    public static final BoilingBloodFluid.Source BOILING_BLOOD = Registry.register(BuiltInRegistries.FLUID, RipplesFabric.id("boiling_blood"), new BoilingBloodFluid.Source());
    public static final BoilingBloodBlock BOILING_BLOOD_BLOCK;
    public static final SlumberingPillarBlock SLUMBERING_PILLARMAN;
    public static final BucketItem BOILING_BLOOD_BUCKET;
    public static final BlockItem SLUMBERING_ITEM;
    public static final BlockEntityType<SlumberingPillarBlockEntity> SLUMBERING_BLOCK_ENTITY;
    public static final EntityType<EngravingEntity> ENGRAVING;
    public static final SimpleParticleType BOILING_BLOOD_POP = Registry.register(BuiltInRegistries.PARTICLE_TYPE, RipplesFabric.id("boiling_blood"), FabricParticleTypes.simple(false));
    static {
        var bloodKey = ResourceKey.create(Registries.BLOCK, RipplesFabric.id("boiling_blood"));
        BOILING_BLOOD_BLOCK = Registry.register(BuiltInRegistries.BLOCK, bloodKey, new BoilingBloodBlock(BOILING_BLOOD,
                BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA).setId(bloodKey).noCollision().randomTicks().strength(100).lightLevel(state -> 15).noLootTable()));
        var pillarKey = ResourceKey.create(Registries.BLOCK, RipplesFabric.id("slumbering_pillarman"));
        SLUMBERING_PILLARMAN = Registry.register(BuiltInRegistries.BLOCK, pillarKey, new SlumberingPillarBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).setId(pillarKey).noOcclusion().noLootTable().isValidSpawn((state, level, pos, type) -> false)));
        var bucketKey = ResourceKey.create(Registries.ITEM, RipplesFabric.id("boiling_blood_bucket"));
        BOILING_BLOOD_BUCKET = Registry.register(BuiltInRegistries.ITEM, bucketKey, new BucketItem(BOILING_BLOOD, new Item.Properties().setId(bucketKey)) {
            @Override protected void playEmptySound(LivingEntity user, LevelAccessor level, BlockPos pos) {
                level.playSound(user, pos, JojoSounds.get("bucket_empty_boiling_blood"), SoundSource.BLOCKS, 1, 1);
            }
        });
        var itemKey = ResourceKey.create(Registries.ITEM, RipplesFabric.id("slumbering_pillarman"));
        SLUMBERING_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(SLUMBERING_PILLARMAN, new Item.Properties().setId(itemKey).rarity(Rarity.EPIC)));
        SLUMBERING_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RipplesFabric.id("slumbering_pillarman"),
                FabricBlockEntityTypeBuilder.create(SlumberingPillarBlockEntity::new, SLUMBERING_PILLARMAN).build());
        var engravingKey = ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("pillarman_temple_engraving"));
        ENGRAVING = Registry.register(BuiltInRegistries.ENTITY_TYPE, engravingKey, EntityType.Builder.<EngravingEntity>of(EngravingEntity::new, MobCategory.MISC)
                .sized(.5F, .5F).noSummon().clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).build(engravingKey));
    }
    private record Config(boolean pillarManTempleSpawn) { }
    private static Config config = new Config(true);
    public static boolean templeSpawnEnabled() { return config.pillarManTempleSpawn(); }
    public static void initialize() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("jojo/pillar_world.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!json.has("pillarManTempleSpawn") || !json.get("pillarManTempleSpawn").isJsonPrimitive()
                        || !json.getAsJsonPrimitive("pillarManTempleSpawn").isBoolean()) throw new IllegalArgumentException("pillarManTempleSpawn must be boolean");
                config = new Config(json.get("pillarManTempleSpawn").getAsBoolean());
            } else { Files.createDirectories(path.getParent()); Files.writeString(path, gson.toJson(config) + "\n"); }
        } catch (Exception exception) { throw new IllegalStateException("Invalid Pillar world configuration: " + path, exception); }
        PillarTempleStructure.initialize();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(SLUMBERING_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(BOILING_BLOOD_BUCKET));
    }
    /** Original hanging decoration. Its 48×48 artwork survives without supporting blocks and drops nothing. */
    public static final class EngravingEntity extends HangingEntity {
        private static final EntityDataAccessor<Integer> TEXTURE = SynchedEntityData.defineId(EngravingEntity.class, EntityDataSerializers.INT);
        public EngravingEntity(EntityType<? extends EngravingEntity> type, Level level) { super(type, level); }
        public EngravingEntity(Level level, BlockPos pos, Direction direction, int texture) {
            super(ENGRAVING, level, pos); setTextureId(texture); setDirection(direction);
        }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(TEXTURE, 0); }
        public int getTextureId() { return entityData.get(TEXTURE); }
        public void setTextureId(int value) { entityData.set(TEXTURE, value >= 0 && value < 8 ? value : 0); }
        @Override public boolean survives() { return true; }
        @Override protected AABB calculateBoundingBox(BlockPos anchor, Direction direction) {
            double x = anchor.getX() + (direction.getAxis() == Direction.Axis.Z ? .5 : 0), y = anchor.getY() + .5,
                    z = anchor.getZ() + (direction.getAxis() == Direction.Axis.X ? .5 : 0);
            double dx = direction.getAxis() == Direction.Axis.Z ? 1.5 : 0, dz = direction.getAxis() == Direction.Axis.X ? 1.5 : 0;
            return new AABB(x - dx, y - 1.5, z - dz, x + dx, y + 1.5, z + dz);
        }
        @Override public void dropItem(ServerLevel level, Entity breaker) { }
        @Override public void playPlacementSound() { }
        @Override protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output); output.putByte("Facing", (byte) getDirection().get2DDataValue()); output.putInt("TexId", getTextureId());
        }
        @Override protected void readAdditionalSaveData(ValueInput input) {
            // The original templates predate block_pos. Calling the modern superclass first
            // logs an invalid-position error for each otherwise valid legacy engraving.
            BlockPos entityPos = blockPosition();
            BlockPos anchor = input.read("block_pos", BlockPos.CODEC).orElseGet(() -> {
                var x = input.getInt("TileX");
                var y = input.getInt("TileY");
                var z = input.getInt("TileZ");
                return x.isPresent() && y.isPresent() && z.isPresent()
                        ? new BlockPos(x.get(), y.get(), z.get()) : entityPos;
            });
            // Match vanilla's distance validation. Structure placement moves Pos before
            // loading, but cannot transform obsolete TileX/Y/Z; use that relocated Pos.
            pos = anchor.closerThan(entityPos, 16) ? anchor : entityPos;
            setTextureId(input.getIntOr("TexId", 0));
            setDirection(Direction.from2DDataValue(input.getByteOr("Facing", (byte) 0)));
        }
    }
    private PillarWorldContent() { }
}
