package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.fabric.pillarman.PossessionService;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import java.util.function.Predicate;

/** StandUtil.playerCanSeeStands/playerCanHearStands and true pre-possession spectator state. */
public final class StandPerception {
    public static final Holder<MobEffect> SPIRIT_VISION=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("spirit_vision"),new MobEffect(MobEffectCategory.BENEFICIAL,0x8E45FF){});
    public static final Holder<MobEffect> INTEGRATED_STAND=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("integrated_stand"),new MobEffect(MobEffectCategory.BENEFICIAL,0x8E45FF){});
    private static Predicate<Player> clientSpectator=Player::isSpectator;
    public static void setClientSpectator(Predicate<Player> predicate){clientSpectator=java.util.Objects.requireNonNull(predicate);}
    public static boolean trueSpectator(Player player){return player instanceof ServerPlayer server?PossessionService.actualGameMode(server)==GameType.SPECTATOR:clientSpectator.test(player);}
    public static boolean canSeeStand(Player player){return player!=null&&(trueSpectator(player)||player.getAttachedOrCreate(StandAttachments.PROGRESS).hasStand()||player.hasEffect(SPIRIT_VISION));}
    public static boolean canHearStand(Player player){return canSeeStand(player);}
    /** Source invisible Stand sounds reach only eligible listeners within vanilla sound range. */
    public static void playSound(Entity source,String name,float volume,float pitch){
        playSound(source,JojoSounds.get(name),volume,pitch);
    }
    public static void playSound(Entity source,net.minecraft.sounds.SoundEvent sound,float volume,float pitch){
        if(source.isSilent()||!(source.level() instanceof ServerLevel level))return;
        var holder=BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        double radius=volume>1?16*volume:16;
        var packet=new net.minecraft.network.protocol.game.ClientboundSoundPacket(holder,SoundSource.PLAYERS,source.getX(),source.getY(),source.getZ(),volume,pitch,level.getRandom().nextLong());
        for(var player:level.players())if(canHearStand(player)&&player.distanceToSqr(source)<radius*radius)player.connection.send(packet);
    }
    public static boolean uncurable(Holder<MobEffect> effect){return effect.equals(SPIRIT_VISION)||effect.equals(INTEGRATED_STAND);}
    public static void initialize(){}
    private StandPerception(){}
}
