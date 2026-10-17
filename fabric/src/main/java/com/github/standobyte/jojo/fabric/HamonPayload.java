package com.github.standobyte.jojo.fabric;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Skill UI sends bounded intent. The server computes teacher availability and all eligibility. */
public record HamonPayload(Operation operation,String id,int targetId,boolean enabled) implements CustomPacketPayload {
    public enum Operation { OPEN, READ_INTRO, LEARN, TECHNIQUE, RESET, MEDITATE, ABANDON, ASK, TEACH }
    public static final Type<HamonPayload> TYPE=new Type<>(RipplesFabric.id("hamon_progression"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeByte(p.operation().ordinal());b.writeUtf(p.id(),128);b.writeVarInt(p.targetId());b.writeBoolean(p.enabled());
    },b->{int ordinal=b.readUnsignedByte();if(ordinal>=Operation.values().length)throw new DecoderException("Invalid Hamon UI operation");
        return new HamonPayload(Operation.values()[ordinal],b.readUtf(128),b.readVarInt(),b.readBoolean());});
    @Override public Type<HamonPayload> type() {return TYPE;}
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(payload,context)->{
            ServerPlayer player=context.player();if(!HamonService.canAct(player))return;
            switch(payload.operation()) {
                case OPEN -> HamonService.refreshTeachers(player);
                case READ_INTRO -> HamonService.readIntro(player);
                case LEARN -> HamonService.learnSkill(player,payload.id());
                case TECHNIQUE -> HamonService.pickTechnique(player,payload.id());
                case RESET -> HamonService.resetSkills(player,payload.id());
                case MEDITATE -> HamonService.setMeditating(player,payload.enabled());
                case ABANDON -> HamonService.abandon(player);
                case ASK -> {if(player.level().getEntity(payload.targetId()) instanceof LivingEntity teacher)HamonService.interactTeacher(player,teacher);}
                case TEACH -> {if(player.level().getEntity(payload.targetId()) instanceof ServerPlayer learner)HamonService.teach(player,learner);}
            }
        });
    }
}
