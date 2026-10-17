package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Original material, kept independently of the transient lifeform and never sent as private NBT. */
public final class GETransformationData {
    public enum FollowMode { TRACK, AGGRO_TRACK, AGGRO_FORGETFUL, DELIVERY }
    private CompoundTag entityData;
    private Entity sourceEntity;
    private BlockState block;
    private BlockPos position;
    private CompoundTag blockEntityData;
    private UUID followTarget;
    private FollowMode followMode;

    public static CompoundTag saveEntity(Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess());
        entity.saveWithoutId(output);
        output.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return output.buildResult();
    }
    public static Entity loadEntity(CompoundTag data, Level level) {
        return EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data), level,
                new EntitySpawnRequest(EntitySpawnReason.LOAD, true)).orElse(null);
    }
    public GETransformationData entity(Entity entity) {
        sourceEntity = entity; entityData = saveEntity(entity); block = null; position = null; blockEntityData = null; return this;
    }
    public GETransformationData block(BlockState state, BlockPos pos, BlockEntity blockEntity) {
        block = state; position = pos.immutable(); blockEntityData = blockEntity == null ? null : blockEntity.saveWithFullMetadata(blockEntity.getLevel().registryAccess());
        sourceEntity = null; entityData = null; return this;
    }
    public GETransformationData follow(UUID target, FollowMode mode, LivingEntity user) {
        if (target != null && user != null && target.equals(user.getUUID())) {
            if (mode == FollowMode.AGGRO_TRACK) mode = FollowMode.TRACK;
            else if (mode == FollowMode.AGGRO_FORGETFUL) mode = null;
        }
        followTarget = mode == null ? null : target; followMode = followTarget == null ? null : mode; return this;
    }
    public Entity entity(Level level) {
        if (sourceEntity == null && entityData != null) sourceEntity = loadEntity(entityData, level);
        return sourceEntity;
    }
    public BlockState block() { return block; }
    public void rotatedBlock(BlockState value) { block = value; }
    public BlockPos originalPosition() { return position; }
    public UUID followTarget() { return followTarget; }
    public FollowMode followMode() { return followMode; }
    public CompoundTag blockEntityData() { return blockEntityData == null ? null : blockEntityData.copy(); }
    public boolean hasMaterial() { return entityData != null || block != null; }
    public BlockEntity blockEntity(BlockPos at, Level level) {
        return block != null && blockEntityData != null ? BlockEntity.loadStatic(at, block, blockEntityData, level.registryAccess()) : null;
    }
    public GETransformationData copy(Level level) { return read(write(level), level); }
    public List<MobEffectInstance> productEffects(Level level) {
        ItemStack item = itemView(level);
        var inherited = item.get(GEAnimalProducts.EFFECTS);
        if (inherited != null) return inherited.stream().map(MobEffectInstance::new).toList();
        var potion = item.get(DataComponents.POTION_CONTENTS);
        if (potion == null) return List.of();
        return java.util.stream.StreamSupport.stream(potion.getAllEffects().spliterator(), false).map(MobEffectInstance::new).toList();
    }
    public ItemStack itemView(Level level) {
        Entity entity = entity(level);
        if (entity instanceof ItemEntity item) return item.getItem().copy();
        if (entity != null) {
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                if (projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) return arrow.getPickupItemStackOrigin().copy();
                if (projectile instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile thrown) return thrown.getItem().copy();
            }
            ItemStack picked = entity.getPickResult();
            if (picked != null && !picked.isEmpty()) return picked.copy();
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (type.equals("minecraft:tnt")) return new ItemStack(Items.TNT);
            if (type.equals("minecraft:end_crystal")) return new ItemStack(Items.END_CRYSTAL);
            if (type.equals("jojo:road_roller")) {
                var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("jojo", "road_roller"));
                if (item != null) return new ItemStack(item);
            }
        }
        return block == null ? ItemStack.EMPTY : new ItemStack(block.getBlock().asItem());
    }
    public Component name(Level level) {
        Entity entity = entity(level);
        return entity != null ? entity.getDisplayName() : block != null ? block.getBlock().getName() : Component.empty();
    }
    public CompoundTag write(Level level) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (entityData != null) output.store("GESourceEntity", CompoundTag.CODEC, entityData);
        if (block != null) output.store("GESourceBlock", BlockState.CODEC, block);
        if (position != null) output.store("GESourceBlockPos", BlockPos.CODEC, position);
        if (blockEntityData != null) output.store("GESourceBlockEntity", CompoundTag.CODEC, blockEntityData);
        if (followTarget != null) {
            output.store("FollowTarget", UUIDUtil.CODEC, followTarget);
            output.putString("FollowMode", followMode.name());
        }
        return output.buildResult();
    }
    public static GETransformationData read(CompoundTag tag, Level level) {
        var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
        var data = new GETransformationData();
        data.entityData = input.read("GESourceEntity", CompoundTag.CODEC).map(CompoundTag::copy).orElse(null);
        data.block = input.read("GESourceBlock", BlockState.CODEC).orElse(null);
        data.position = input.read("GESourceBlockPos", BlockPos.CODEC).orElse(null);
        data.blockEntityData = input.read("GESourceBlockEntity", CompoundTag.CODEC).map(CompoundTag::copy).orElse(null);
        data.followTarget = input.read("FollowTarget", UUIDUtil.CODEC).orElse(null);
        try { data.followMode = FollowMode.valueOf(input.getStringOr("FollowMode", "")); }
        catch (IllegalArgumentException invalid) { data.followTarget = null; }
        return data;
    }
}
