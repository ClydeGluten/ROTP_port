package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;
import java.util.*;

/** CustomExplosion Mode.NONE: ray candidates determine fire, never bulk destruction or restoration history. */
public final class MRCrossfireExplosion {
    public static void explode(ServerLevel server,MRCrossfireEntity projectile){
        float radius=(projectile.small()?1:3)*projectile.scale();Vec3 origin=projectile.position();
        var damage=new DamageSource(server.registryAccess().getOrThrow(DamageTypes.EXPLOSION),projectile,projectile.getOwner());
        var calculator=new EntityBasedExplosionDamageCalculator(projectile);
        var context=new ServerExplosion(server,projectile,damage,calculator,origin,radius,true,Explosion.BlockInteraction.KEEP);
        Set<BlockPos> candidates=calculateBlocks(server,projectile,context,calculator,radius);
        double diameter=radius*2;var user=projectile.user();boolean protectUser=user instanceof ServerPlayer player&&ProgressionService.state(player).level()>=4;
        var box=new AABB(Math.floor(origin.x-diameter-1),Math.floor(origin.y-diameter-1),Math.floor(origin.z-diameter-1),
                Math.floor(origin.x+diameter+1),Math.floor(origin.y+diameter+1),Math.floor(origin.z+diameter+1));
        for(var entity:server.getEntities(projectile,box)){
            if(entity==projectile.getOwner()||protectUser&&entity==user||entity.ignoreExplosion(context))continue;
            double ratio=entity.position().distanceTo(origin)/diameter;if(ratio>1)continue;
            var delta=(entity instanceof PrimedTnt?entity.position():entity.getEyePosition()).subtract(origin);if(delta.length()<=1E-4)continue;
            double impact=(1-ratio)*ServerExplosion.getSeenPercent(origin,entity);
            float amount=(float)((impact*impact+impact)/2*7*diameter+1);
            // The original custom-explosion boundary does not apply the Stand power config multiplier.
            if(projectile.small()&&entity instanceof LivingEntity living)StandDamage.hurt(server,living,damage,amount);else entity.hurtServer(server,damage,amount);
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(impact)));
            if(entity instanceof ServerPlayer player&&!player.isSpectator()&&(!player.isCreative()||!player.getAbilities().flying))player.connection.send(new ClientboundSetEntityMotionPacket(player));
            MRProjectileEntity.ignite(entity,10);
            if(user instanceof ServerPlayer player&&givesResolve(entity))MagiciansRedActions.trainCrossfire(player,.03125F);
        }
        if(server.getGameRules().get(GameRules.MOB_GRIEFING))for(var pos:candidates){
            if(!MRProjectileEntity.permitted(server,pos,projectile.getOwner()))continue;
            if(server.isEmptyBlock(pos))MRFlameEntity.placeFire(server,pos,projectile.getOwner());
            else if(!projectile.small()&&!MRFlameEntity.meltIceAndSnow(server,server.getBlockState(pos),pos))server.getRandom().nextFloat();
        }
        server.playSound(null,origin.x,origin.y,origin.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.BLOCKS,4,(1+(server.getRandom().nextFloat()-server.getRandom().nextFloat())*.2F)*.7F);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,origin.x,origin.y,origin.z,0,1,0,0,1);
    }
    private static boolean givesResolve(Entity entity){
        return entity.isAlive()&&(entity instanceof Player||entity instanceof StandManifestation||entity.getType().getCategory()==MobCategory.MONSTER||entity instanceof Mob mob&&mob.isAggressive());
    }
    private static Set<BlockPos> calculateBlocks(ServerLevel server,MRCrossfireEntity projectile,Explosion context,ExplosionDamageCalculator calculator,float radius){
        Set<BlockPos> result=new HashSet<>();
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){
            if(x!=0&&x!=15&&y!=0&&y!=15&&z!=0&&z!=15)continue;
            var step=new Vec3(x/15F*2-1,y/15F*2-1,z/15F*2-1).normalize().scale(.3);Vec3 at=projectile.position();
            for(float power=radius*(.7F+server.getRandom().nextFloat()*.6F);power>0;power-=.225F){
                var pos=BlockPos.containing(at);var state=server.getBlockState(pos);var fluid=server.getFluidState(pos);
                var resistance=calculator.getBlockExplosionResistance(context,server,pos,state,fluid);if(resistance.isPresent())power-=(resistance.get()+.3F)*.3F;
                if(power>0&&calculator.shouldBlockExplode(context,server,pos,state,power))result.add(pos);at=at.add(step);
            }
        }
        return result;
    }
    private MRCrossfireExplosion(){}
}
