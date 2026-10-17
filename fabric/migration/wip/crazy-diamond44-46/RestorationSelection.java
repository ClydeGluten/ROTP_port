package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import com.github.standobyte.jojo.fabric.StandAttachments;
import com.github.standobyte.jojo.fabric.StandManifestation;
import com.github.standobyte.jojo.fabric.stand.restoration.BrokenBlockRecord;
import com.github.standobyte.jojo.fabric.stand.restoration.RestorationHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Server target selection and tracked action/result state; client rendering is plan 46's responsibility. */
public final class RestorationSelection {
    public enum Mode { NONE, REPAIR, PREVIOUS_STATE, TERRAIN, HEAL, ANCHOR }
    public record State(Mode mode, int ticks, int targetId, boolean entityWorked, List<BlockPos> restored, long revision) {
        public static final State NONE = new State(Mode.NONE, 0, -1, false, List.of(), 0);
        public State { restored = List.copyOf(restored); }
    }
    private static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM = StreamCodec.of((buffer, value) -> {
        buffer.writeEnum(value.mode); buffer.writeVarInt(value.ticks); buffer.writeInt(value.targetId);
        buffer.writeBoolean(value.entityWorked); buffer.writeVarInt(value.restored.size());
        value.restored.forEach(buffer::writeBlockPos); buffer.writeLong(value.revision);
    }, buffer -> {
        var mode = buffer.readEnum(Mode.class); int ticks = buffer.readVarInt(), target = buffer.readInt();
        boolean worked = buffer.readBoolean(); int count = buffer.readVarInt();
        if (ticks < 0 || count < 0 || count > 64) throw new IllegalArgumentException("Invalid Crazy Diamond restoration state");
        var positions = new ArrayList<BlockPos>(count);
        for (int i = 0; i < count; i++) positions.add(buffer.readBlockPos());
        return new State(mode, ticks, target, worked, positions, buffer.readLong());
    });
    public static final AttachmentType<State> DATA = AttachmentRegistry.<State>builder().initializer(() -> State.NONE)
            .syncWith(STREAM, AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("cd_restoration_action"));

    /** Plan 45's entity supplies its real attributes, stamina condition, and range efficiency. */
    public interface Stand extends StandManifestation {
        double restorationAttackSpeed();
        void prepareRestorationSummon(Mode mode, boolean newlySummoned);
        boolean canUseRestoration(Mode mode);
        void beginRestoration();
    }
    public record Target(HitResult.Type type, Entity entity) { }

    public static Entity camera(ServerPlayer player, StandManifestation stand) {
        return stand.manuallyControlled() ? stand.entity() : player;
    }

    public static Target target(ServerPlayer player, StandManifestation stand) {
        return target(player, stand, false);
    }
    public static Target target(ServerPlayer player, StandManifestation stand, boolean allowOwnStand) {
        var camera = camera(player, stand);
        var start = camera.getEyePosition();
        double range = camera == player ? player.blockInteractionRange() : 2.5;
        var end = start.add(camera.getLookAngle().scale(range));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        double distance = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(start);
        var hit = ProjectileUtil.getEntityHitResult(camera, start, end,
                camera.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                entity -> entity != player && (allowOwnStand || entity != stand.entity()) && !entity.isSpectator() && !entity.isRemoved() && entity.isPickable(), distance);
        return hit != null ? new Target(HitResult.Type.ENTITY, hit.getEntity()) : new Target(block.getType(), null);
    }

    public static Stream<BrokenBlockRecord> terrain(ServerPlayer player, StandManifestation stand) {
        var camera = camera(player, stand);
        var eye = RestorationHooks.eyePos(camera);
        boolean resolve = StandAttachments.resolve(player).isBurst();
        return RestorationHooks.getBlocksInRange(player.level(), player, eye, 12, block ->
                RestorationHooks.blockPosSelectedForRestoration(block, camera.getLookAngle(), camera.getEyePosition(), eye,
                        resolve, player.isShiftKeyDown()));
    }

    public static void publish(Mob stand, Mode mode, int ticks, Entity target, boolean worked, List<BlockPos> restored) {
        stand.setAttached(DATA, new State(mode, ticks, target == null ? -1 : target.getId(), worked, restored, stand.level().getGameTime()));
    }
    public static void clear(Mob stand) { stand.setAttached(DATA, State.NONE); }
    public static State state(Entity stand) { return stand.getAttachedOrCreate(DATA); }
    public static void initialize() { }
    private RestorationSelection() { }
}
