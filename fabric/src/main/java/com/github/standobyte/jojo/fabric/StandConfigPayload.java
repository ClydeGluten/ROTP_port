package com.github.standobyte.jojo.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Read-only server configuration; clients cannot choose their own stamina or damage multipliers. */
public record StandConfigPayload(StandConfig.Data data) implements CustomPacketPayload {
    public static final Type<StandConfigPayload> TYPE=new Type<>(RipplesFabric.id("stand_config"));
    public static final StreamCodec<FriendlyByteBuf,StandConfigPayload> CODEC=StreamCodec.of((b,p)->{
        var d=p.data();b.writeBoolean(d.keepStandOnDeath());b.writeBoolean(d.skipStandProgression());b.writeBoolean(d.standStamina());
        b.writeBoolean(d.soulAscension());b.writeBoolean(d.endermenBeyondTimeSpace());b.writeBoolean(d.saveDestroyedBlocks());
        b.writeVarInt(d.resolveLvlPoints().size());for(float v:d.resolveLvlPoints())b.writeFloat(v);
        b.writeVarInt(d.timeStopChunkRange());b.writeFloat(d.timeStopDamageMultiplier());b.writeFloat(d.standPowerMultiplier());b.writeFloat(d.standResistanceMultiplier());
    },b->{
        boolean keep=b.readBoolean(),skip=b.readBoolean(),stamina=b.readBoolean(),soul=b.readBoolean(),endermen=b.readBoolean(),save=b.readBoolean();
        int count=b.readVarInt();if(count<1||count>1024)throw new io.netty.handler.codec.DecoderException("Invalid Resolve threshold count");
        var points=new java.util.ArrayList<Float>(count);for(int i=0;i<count;i++)points.add(b.readFloat());
        return new StandConfigPayload(new StandConfig.Data(keep,skip,stamina,soul,endermen,save,points,b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat()));
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
