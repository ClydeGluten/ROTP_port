package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Only presentation flags are public; private blood and progression remain owner-only. */
public final class VampirePresentation {
    public record Visual(boolean glowing, int clawTick, int fadeTicks, boolean leftHanded) {
        public static final Visual EMPTY=new Visual(false,0,0,false);
    }
    public static final AttachmentType<Boolean> GLOW_EYES=AttachmentRegistry.<Boolean>builder().initializer(()->true)
            .persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("vampire_glowing_eyes"));
    public static final AttachmentType<Visual> VISUAL=AttachmentRegistry.<Visual>builder().initializer(()->Visual.EMPTY)
            .syncWith(StreamCodec.<RegistryFriendlyByteBuf,Visual>of((b,v)->{b.writeBoolean(v.glowing());b.writeVarInt(v.clawTick());b.writeVarInt(v.fadeTicks());b.writeBoolean(v.leftHanded());},
                    b->new Visual(b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readBoolean())),AttachmentSyncPredicate.all())
            .buildAndRegister(RipplesFabric.id("vampire_visual"));
    public record Settings(boolean glowingEyes) implements CustomPacketPayload {
        public static final Type<Settings> TYPE=new Type<>(RipplesFabric.id("vampire_cosmetics"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Settings> CODEC=StreamCodec.of((b,v)->b.writeBoolean(v.glowingEyes()),b->new Settings(b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void initialize(){
        PayloadTypeRegistry.serverboundPlay().register(Settings.TYPE,Settings.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Settings.TYPE,(packet,context)->context.player().setAttached(GLOW_EYES,packet.glowingEyes()));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(ServerPlayer player:server.getPlayerList().getPlayers()){
                if (TimeStopService.isFrozen(player)) continue;
                var old=player.getAttachedOrCreate(VISUAL);var vampire=VampireService.progress(player);
                int tick=player.getAttachedOrCreate(VampireAttachments.HUD).clawTicks();
                int fade=tick>0?0:old.clawTick()>0?17:Math.max(0,old.fadeTicks()-1);
                var next=new Visual(vampire.vampire() && vampire.blood()>=vampire.maxBlood()*.8F && player.getAttachedOrCreate(GLOW_EYES),
                        tick,fade,player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT);
                if(!next.equals(old))player.setAttached(VISUAL,next);
            }
            if(VampireConfig.server().undeadSunDamage())for(var level:server.getAllLevels())
                for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.LivingEntity living
                        && !(entity instanceof net.minecraft.world.entity.player.Player) && living.isAlive()
                        && living.typeHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                        && living.getType()!=net.minecraft.world.entity.EntityTypes.WITHER && !TimeStopService.isFrozen(living)) tickUndeadSun(living);
        });
    }
    private static void tickUndeadSun(net.minecraft.world.entity.LivingEntity entity){
        var level=(net.minecraft.server.level.ServerLevel)entity.level();
        if(entity.damageCooldownTime>10 || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()
                || !level.isBrightOutside() || level.isRaining() || level.isThundering() || entity.getLightLevelDependentMagicValue()<=.5F
                || !level.canSeeSky(net.minecraft.core.BlockPos.containing(entity.getX(),Math.round(entity.getY(1)),entity.getZ())))return;
        var holder=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,RipplesFabric.id("ultraviolet")));
        if(entity.hurtServer(level,new net.minecraft.world.damagesource.DamageSource(holder),4)){
            var old=entity.getEffect(VampireItems.SUN_BURN);int duration=old==null?60:old.getDuration()+60/Math.max(level.getDifficulty().getId(),1);
            int amplifier=old==null?0:duration/60;
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(VampireItems.SUN_BURN,duration,amplifier,false,false,false));
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,duration,amplifier,false,false,true));
        }
    }
    private VampirePresentation() { }
}
