package com.github.standobyte.jojo.fabric.stand.tracking;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Owner-only snapshots. The client cannot submit a holder, stack, position or tracking identity. */
public record ItemTrackingPayload(UUID owner, Identifier dimension, List<View> views) implements CustomPacketPayload {
    public record View(UUID id, ItemStack stack, ItemTrackingService.Kind kind, int entityId, Optional<BlockPos> block) {
        public View { stack = stack.copy(); block = block.map(BlockPos::immutable); }
        @Override public ItemStack stack() { return stack.copy(); }
        static View of(ItemTrackingService.Resolved resolved) {
            return new View(resolved.identity().id(), resolved.stack(), resolved.location().kind(),
                    resolved.entity() == null ? -1 : resolved.entity().getId(), resolved.location().block());
        }
        public Optional<Vec3> marker(Level level, float partialTick) {
            Entity entity = entityId < 0 ? null : level.getEntity(entityId);
            if (entity != null) return Optional.of(entity.getPosition(partialTick).add(0, entity.getBbHeight() + .25, 0));
            return block.map(pos -> Vec3.atBottomCenterOf(pos).add(0, 1, 0));
        }
        @Override public boolean equals(Object other) {
            return other instanceof View v && id.equals(v.id) && kind == v.kind && entityId == v.entityId
                    && block.equals(v.block) && ItemStack.matches(stack, v.stack);
        }
        @Override public int hashCode() { return Objects.hash(id, kind, entityId, block); }
    }
    public static final Type<ItemTrackingPayload> TYPE = new Type<>(RipplesFabric.id("tracked_item_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemTrackingPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeByte(1); b.writeUUID(p.owner); b.writeUtf(p.dimension.toString(), 256); b.writeVarInt(p.views.size());
        for (View v : p.views) {
            b.writeUUID(v.id); ItemStack.STREAM_CODEC.encode(b, v.stack); b.writeByte(v.kind.ordinal()); b.writeVarInt(v.entityId);
            b.writeBoolean(v.block.isPresent()); v.block.ifPresent(b::writeBlockPos);
        }
    }, b -> {
        if (b.readUnsignedByte() != 1) throw new DecoderException("Unsupported item tracking snapshot");
        UUID owner = b.readUUID(); Identifier dimension = Identifier.tryParse(b.readUtf(256)); int size = b.readVarInt();
        if (dimension == null || size < 0 || size > 128) throw new DecoderException("Invalid item tracking bounds");
        var views = new ArrayList<View>(); var ids = new HashSet<UUID>();
        for (int i = 0; i < size; i++) {
            UUID id = b.readUUID(); ItemStack stack = ItemStack.STREAM_CODEC.decode(b); int kind = b.readUnsignedByte(); int entity = b.readVarInt();
            var block = b.readBoolean() ? Optional.of(b.readBlockPos()) : Optional.<BlockPos>empty();
            if (kind >= ItemTrackingService.Kind.values().length || entity < -1 || !ids.add(id) || stack.getCount() != 1
                    || (entity >= 0) == block.isPresent()) throw new DecoderException("Invalid tracked holder");
            views.add(new View(id, stack, ItemTrackingService.Kind.values()[kind], entity, block));
        }
        return new ItemTrackingPayload(owner, dimension, views);
    });
    public ItemTrackingPayload { views = List.copyOf(views); if (views.size() > 128) throw new IllegalArgumentException("Too many tracked items"); }
    @Override public Type<ItemTrackingPayload> type() { return TYPE; }
    private static final Map<Player, ItemTrackingPayload> CLIENT = new WeakHashMap<>();
    public static void receive(Player player, ItemTrackingPayload payload) {
        if (player != null && player.level().isClientSide() && player.getUUID().equals(payload.owner)
                && player.level().dimension().identifier().equals(payload.dimension)) CLIENT.put(player, payload);
    }
    public static List<View> views(Player player) {
        var snapshot = CLIENT.get(player);
        return snapshot != null && player.level().dimension().identifier().equals(snapshot.dimension) ? snapshot.views : List.of();
    }
    public static void clearClient() { CLIENT.clear(); }
    static void send(ServerPlayer owner, List<View> views) {
        if (ServerPlayNetworking.canSend(owner, TYPE)) ServerPlayNetworking.send(owner,
                new ItemTrackingPayload(owner.getUUID(), owner.level().dimension().identifier(), views));
    }
    /** Original encounter packet identifies a loaded entity, never an arbitrary subtype chosen by the client. */
    public record Encounter(int entityId) implements CustomPacketPayload {
        public static final Type<Encounter> TYPE = new Type<>(RipplesFabric.id("lifeform_encounter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Encounter> CODEC = StreamCodec.of(
                (b, p) -> b.writeVarInt(p.entityId), b -> {
                    int id = b.readVarInt(); if (id < 0) throw new DecoderException("Invalid encounter entity"); return new Encounter(id);
                });
        @Override public Type<Encounter> type() { return TYPE; }
    }
    public record EffectView(UUID id, Identifier type, Optional<UUID> target, int targetId, int ticks) { }
    /** Public effect identities/targets only. Private handler NBT never crosses this channel. */
    public record Effects(UUID owner, int ownerId, Identifier dimension, List<EffectView> entries) implements CustomPacketPayload {
        public static final Type<Effects> TYPE = new Type<>(RipplesFabric.id("stand_effect_targets"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Effects> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.owner); b.writeVarInt(p.ownerId); b.writeUtf(p.dimension.toString(), 256); b.writeVarInt(p.entries.size());
            for (var e : p.entries) {
                b.writeUUID(e.id); b.writeUtf(e.type.toString(), 256); b.writeBoolean(e.target.isPresent()); e.target.ifPresent(b::writeUUID);
                b.writeVarInt(e.targetId); b.writeVarInt(e.ticks);
            }
        }, b -> {
            UUID owner = b.readUUID(); int ownerId = b.readVarInt(); Identifier dimension = Identifier.tryParse(b.readUtf(256)); int size = b.readVarInt();
            if (ownerId < 0 || dimension == null || size < 0 || size > 128) throw new DecoderException("Invalid effect snapshot");
            var entries = new ArrayList<EffectView>(); var ids = new HashSet<UUID>();
            for (int i = 0; i < size; i++) {
                UUID id = b.readUUID(); Identifier type = Identifier.tryParse(b.readUtf(256));
                var target = b.readBoolean() ? Optional.of(b.readUUID()) : Optional.<UUID>empty(); int targetId = b.readVarInt(); int ticks = b.readVarInt();
                if (type == null || targetId < -1 || ticks < 0 || !ids.add(id)) throw new DecoderException("Invalid effect entry");
                entries.add(new EffectView(id, type, target, targetId, ticks));
            }
            return new Effects(owner, ownerId, dimension, entries);
        });
        public Effects { entries = List.copyOf(entries); }
        @Override public Type<Effects> type() { return TYPE; }
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Effects.TYPE, Effects.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Encounter.TYPE, Encounter.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Encounter.TYPE, (payload, context) -> {
            Entity entity = context.player().level().getEntity(payload.entityId);
            if (entity != null && entity.distanceToSqr(context.player()) <= 144) LifeformsEncountered.encounter(context.player(), entity);
        });
    }
}
