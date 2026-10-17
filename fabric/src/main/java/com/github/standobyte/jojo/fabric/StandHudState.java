package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Transient, owner-only server snapshots. Clients never submit cooldown or availability values. */
public record StandHudState(int finger, int inhale, int timeStop, int blink, int timePunch,
                            int recovery, int charge, int activeStop,
                            int fingerTotal, int inhaleTotal, int timeStopTotal, int blinkTotal, int timePunchTotal) {
    public static final StandHudState EMPTY = new StandHudState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    /** New owner-only snapshot keeps the old record and its wire layout intact. */
    public record ActionView(Identifier action,int remaining,int total,float training,float maximum,boolean unlocked,boolean parts,boolean available) {}
    public record RegisteredState(String stand,List<ActionView> actions,Optional<Identifier> held,int heldTicks) {
        public static final RegisteredState EMPTY=new RegisteredState("",List.of(),Optional.empty(),0);
        public RegisteredState {actions=List.copyOf(actions);if(actions.size()>512||stand.length()>256||heldTicks<0)throw new IllegalArgumentException("Invalid action HUD bounds");}
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,RegisteredState> REGISTERED_CODEC=StreamCodec.of((b,s)->{
        b.writeByte(1);b.writeUtf(s.stand,256);b.writeVarInt(s.actions.size());
        for(var a:s.actions){b.writeUtf(a.action.toString(),256);b.writeVarInt(a.remaining);b.writeVarInt(a.total);b.writeFloat(a.training);b.writeFloat(a.maximum);b.writeBoolean(a.unlocked);b.writeBoolean(a.parts);b.writeBoolean(a.available);}
        b.writeBoolean(s.held.isPresent());s.held.ifPresent(id->b.writeUtf(id.toString(),256));b.writeVarInt(s.heldTicks);
    },b->{
        if(b.readUnsignedByte()!=1)throw new io.netty.handler.codec.DecoderException("Unknown action HUD version");
        String stand=b.readUtf(256);int count=b.readVarInt();if(count<0||count>512)throw new io.netty.handler.codec.DecoderException("Action HUD limit exceeded");
        var entries=new ArrayList<ActionView>(count);
        for(int i=0;i<count;i++){
            var id=Identifier.tryParse(b.readUtf(256));int remaining=b.readVarInt(),total=b.readVarInt();float training=b.readFloat(),maximum=b.readFloat();
            if(id==null||remaining<0||total<0||!Float.isFinite(training)||training< -1||!Float.isFinite(maximum)||maximum<0)throw new io.netty.handler.codec.DecoderException("Invalid action HUD entry");
            entries.add(new ActionView(id,remaining,total,training,maximum,b.readBoolean(),b.readBoolean(),b.readBoolean()));
        }
        var held=b.readBoolean()?Optional.of(Identifier.parse(b.readUtf(256))):Optional.<Identifier>empty();
        return new RegisteredState(stand,entries,held,b.readVarInt());
    });
    public static final AttachmentType<RegisteredState> REGISTERED=AttachmentRegistry.<RegisteredState>builder().initializer(()->RegisteredState.EMPTY)
            .syncWith(REGISTERED_CODEC,AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("registered_stand_hud"));
    public static RegisteredState captureRegistered(ServerPlayer player){
        var progress=player.getAttachedOrCreate(StandAttachments.PROGRESS);if(!player.isAlive()||!progress.hasStand())return RegisteredState.EMPTY;
        var entries=new ArrayList<ActionView>();
        for(var d:StandActionRegistry.actions(progress.standId())){
            boolean unlocked=d.delegated()?ProgressionService.unlocked(player,Math.max(0,d.resolveLevel())):StandActionTraining.unlocked(player,d.id());
            float maximum=StandActionRegistry.maximumTraining(player,d);
            float training=d.delegated()?(d.trained()?ProgressionService.state(player).trainingPoints():unlocked?maximum:-1):StandActionTraining.points(player,d.id(),maximum);
            boolean time=d.id().getPath().endsWith("_time_stop");
            entries.add(new ActionView(d.id(),time?TimeStopService.cooldownRemaining(player):AbilityCooldowns.remaining(player,d.id()),
                    time?TimeStopService.cooldownDuration(player):AbilityCooldowns.duration(player,d.id()),training,maximum,unlocked,
                    StandActionRegistry.partsAvailable(player,d),unlocked&&StandActionRegistry.available(player,d)));
        }
        return new RegisteredState(progress.standId(),entries,StandActionRegistry.held(player),StandActionRegistry.heldTicks(player));
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, StandHudState> STREAM_CODEC = StreamCodec.of(
            (b, s) -> { b.writeVarInt(s.finger); b.writeVarInt(s.inhale); b.writeVarInt(s.timeStop);
                b.writeVarInt(s.blink); b.writeVarInt(s.timePunch); b.writeVarInt(s.recovery); b.writeVarInt(s.charge); b.writeVarInt(s.activeStop);
                b.writeVarInt(s.fingerTotal); b.writeVarInt(s.inhaleTotal); b.writeVarInt(s.timeStopTotal); b.writeVarInt(s.blinkTotal); b.writeVarInt(s.timePunchTotal); },
            b -> new StandHudState(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    public static StandHudState capture(ServerPlayer player) {
        if (!player.isAlive() || !player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()) return EMPTY;
        var stand = StandService.current(player);
        return new StandHudState(AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_FINGER),
                AbilityCooldowns.remaining(player, StarPlatinumEntity.PHASE_INHALE), TimeStopService.cooldownRemaining(player),
                AbilityCooldowns.remaining(player, 11), AbilityCooldowns.remaining(player, 12),
                stand == null ? 0 : stand.uiRecoveryTicks(), TimeStopService.chargeTicksRemaining(player), TimeStopService.ticksRemaining(player),
                AbilityCooldowns.duration(player, StarPlatinumEntity.PHASE_FINGER),
                AbilityCooldowns.duration(player, StarPlatinumEntity.PHASE_INHALE), TimeStopService.cooldownDuration(player),
                AbilityCooldowns.duration(player, 11), AbilityCooldowns.duration(player, 12));
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                StandActionRegistry.tick(player);
                var next = capture(player);
                if (!next.equals(player.getAttachedOrCreate(StandAttachments.HUD))) player.setAttached(StandAttachments.HUD, next);
                var registered=captureRegistered(player);
                if(!registered.equals(player.getAttachedOrCreate(REGISTERED)))player.setAttached(REGISTERED,registered);
            }
        });
    }
}
