package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Public source/target render data follows the original extra-spawn packet; no source block inventory is sent. */
public record GoldExperienceMorphPayload(int entityId, UUID entityUuid, Identifier dimension, int age, int duration,
                                        float itemTime, boolean reverse, Optional<UUID> owner,
                                        Optional<EntityView> source, Optional<EntityView> target,
                                        Optional<BlockState> block, Optional<Vec3> hostOffset) implements CustomPacketPayload {
    public record EntityView(Identifier type, float yaw, float pitch, float headYaw, List<SynchedEntityData.DataValue<?>> data) {
        public EntityView { data = List.copyOf(data); }
        static EntityView of(Entity entity) {
            var data = entity.getEntityData().getNonDefaultValues();
            return new EntityView(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getYRot(), entity.getXRot(), entity.getYHeadRot(), data == null ? List.of() : data);
        }
        public Entity create(Level level) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(type)) return null;
            Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(type).create(level, EntitySpawnReason.LOAD);
            if (entity != null) {
                entity.setYRot(yaw); entity.setXRot(pitch); entity.setYHeadRot(headYaw);
                entity.getEntityData().assignValues(data);
            }
            return entity;
        }
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(type.toString(), 256); buffer.writeFloat(yaw); buffer.writeFloat(pitch); buffer.writeFloat(headYaw);
            buffer.writeVarInt(data.size()); for (var value : data) value.write(buffer);
        }
        private static EntityView read(RegistryFriendlyByteBuf buffer) {
            Identifier type = Identifier.tryParse(buffer.readUtf(256)); float yaw = buffer.readFloat(), pitch = buffer.readFloat(), head = buffer.readFloat();
            int count = buffer.readVarInt();
            if (type == null || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(head) || count < 0 || count > 255)
                throw new DecoderException("Invalid lifeform render entity");
            var data = new ArrayList<SynchedEntityData.DataValue<?>>(); var ids = new HashSet<Integer>();
            for (int i = 0; i < count; i++) {
                int id = buffer.readUnsignedByte(); if (id == 255 || !ids.add(id)) throw new DecoderException("Invalid lifeform synced data");
                data.add(SynchedEntityData.DataValue.read(buffer, id));
            }
            return new EntityView(type, yaw, pitch, head, data);
        }
    }
    public static final Type<GoldExperienceMorphPayload> TYPE = new Type<>(RipplesFabric.id("ge_morph"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GoldExperienceMorphPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.entityId); b.writeUUID(p.entityUuid); b.writeUtf(p.dimension.toString(), 256);
        b.writeVarInt(p.age); b.writeVarInt(p.duration); b.writeFloat(p.itemTime); b.writeBoolean(p.reverse);
        b.writeBoolean(p.owner.isPresent()); p.owner.ifPresent(b::writeUUID);
        b.writeBoolean(p.source.isPresent()); p.source.ifPresent(view -> view.write(b));
        b.writeBoolean(p.target.isPresent()); p.target.ifPresent(view -> view.write(b));
        b.writeBoolean(p.block.isPresent()); p.block.ifPresent(state -> b.writeVarInt(Block.getId(state)));
        b.writeBoolean(p.hostOffset.isPresent()); p.hostOffset.ifPresent(offset -> { b.writeDouble(offset.x); b.writeDouble(offset.y); b.writeDouble(offset.z); });
    }, b -> {
        int id = b.readVarInt(); UUID uuid = b.readUUID(); Identifier dimension = Identifier.tryParse(b.readUtf(256));
        int age = b.readVarInt(), duration = b.readVarInt(); float itemTime = b.readFloat(); boolean reverse = b.readBoolean();
        if (id < 0 || dimension == null || age < 0 || duration < 1 || age > duration || !Float.isFinite(itemTime) || itemTime <= 0)
            throw new DecoderException("Invalid lifeform morph bounds");
        Optional<UUID> owner = b.readBoolean() ? Optional.of(b.readUUID()) : Optional.empty();
        Optional<EntityView> source = b.readBoolean() ? Optional.of(EntityView.read(b)) : Optional.empty();
        Optional<EntityView> target = b.readBoolean() ? Optional.of(EntityView.read(b)) : Optional.empty();
        Optional<BlockState> block = b.readBoolean() ? Optional.of(Block.stateById(b.readVarInt())) : Optional.empty();
        Optional<Vec3> offset = b.readBoolean() ? Optional.of(new Vec3(b.readDouble(), b.readDouble(), b.readDouble())) : Optional.empty();
        if (offset.isPresent() && !Double.isFinite(offset.get().lengthSqr())) throw new DecoderException("Invalid host offset");
        return new GoldExperienceMorphPayload(id, uuid, dimension, age, duration, itemTime, reverse, owner, source, target, block, offset);
    });
    @Override public Type<GoldExperienceMorphPayload> type() { return TYPE; }
    static GoldExperienceMorphPayload of(GETransformationEntity entity) {
        return new GoldExperienceMorphPayload(entity.getId(), entity.getUUID(), entity.level().dimension().identifier(),
                Math.max(0, Math.min(entity.tickCount, entity.duration())), entity.duration(), entity.renderAsItemTime(), entity.turningBack(),
                Optional.ofNullable(entity.ownerUuid()), Optional.ofNullable(entity.source().entity(entity.level())).map(EntityView::of),
                Optional.ofNullable(entity.target()).map(EntityView::of), Optional.ofNullable(entity.source().block()), entity.hostOffset());
    }
    public static void syncTo(Entity entity, ServerPlayer player) {
        if (entity instanceof GETransformationEntity morph && ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, of(morph));
    }
    static void sync(GETransformationEntity entity) {
        if (!entity.level().isClientSide()) for (ServerPlayer player : PlayerLookup.tracking(entity)) syncTo(entity, player);
    }
    private static final Map<Level, Map<Integer, GoldExperienceMorphPayload>> PENDING = new WeakHashMap<>();
    public static void receive(Level level, GoldExperienceMorphPayload payload) {
        if (level == null || !level.dimension().identifier().equals(payload.dimension)) return;
        Entity entity = level.getEntity(payload.entityId);
        if (entity instanceof GETransformationEntity morph && entity.getUUID().equals(payload.entityUuid)) morph.receivePresentation(payload);
        else {
            var pending = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
            if (pending.size() >= 128) pending.remove(pending.keySet().iterator().next());
            pending.put(payload.entityId, payload);
        }
    }
    public static void clientTick(Level level) {
        var pending = PENDING.get(level); if (pending == null) return;
        pending.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey()); if (entity == null) return false;
            if (entity instanceof GETransformationEntity morph && entity.getUUID().equals(entry.getValue().entityUuid)) morph.receivePresentation(entry.getValue());
            return true;
        });
    }
    public static void clearClient() { PENDING.clear(); }
    static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
}
