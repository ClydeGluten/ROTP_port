package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Original map-trade special effect: only the buyer hears music; tracking clients see menacing glyphs. */
public record HamonMapPurchasePayload(int merchantId,int buyerId) implements CustomPacketPayload {
    public static final SimpleParticleType MENACING=Registry.register(BuiltInRegistries.PARTICLE_TYPE,RipplesFabric.id("menacing"),FabricParticleTypes.simple(true));
    public static final Type<HamonMapPurchasePayload> TYPE=new Type<>(RipplesFabric.id("hamon_map_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonMapPurchasePayload> CODEC=StreamCodec.of(
            (buffer,payload)->{buffer.writeVarInt(payload.merchantId());buffer.writeVarInt(payload.buyerId());},
            buffer->new HamonMapPurchasePayload(buffer.readVarInt(),buffer.readVarInt()));
    @Override public Type<HamonMapPurchasePayload> type(){return TYPE;}
    public static void initialize(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
