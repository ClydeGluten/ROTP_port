package com.github.standobyte.jojo.fabric;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record HamonActionPayload(String action,Operation operation) implements CustomPacketPayload {
    public enum Operation {START,KEEP,RELEASE,CANCEL,LEAP}
    public static final Type<HamonActionPayload> TYPE=new Type<>(RipplesFabric.id("hamon_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonActionPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.action,96);b.writeEnum(p.operation);},b->new HamonActionPayload(b.readUtf(96),b.readEnum(Operation.class)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
