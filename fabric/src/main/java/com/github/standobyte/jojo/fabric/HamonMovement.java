package com.github.standobyte.jojo.fabric;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
/** Source liquid walking and oriented four-direction wall travel. */
public final class HamonMovement {
    private static final AttachmentType<Boolean> LIQUID_WALKING=AttachmentRegistry.<Boolean>builder().initializer(()->false)
            .syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("hamon_liquid_walking"));
    private static final class Motion {int waterTick=-100,lastShift=-100;boolean shift,doubleShift,waterContact;}
    public static boolean isWalkingOnLiquid(LivingEntity user){return Boolean.TRUE.equals(user.getAttached(LIQUID_WALKING));}
    private static final Map<UUID,Motion>MOTION=new HashMap<>();
    public static void initialize(){ServerLifecycleEvents.SERVER_STOPPED.register(s->MOTION.clear());}
    public static void tick(LivingEntity user){var m=MOTION.computeIfAbsent(user.getUUID(),id->new Motion());boolean shifted=user.isShiftKeyDown();if(shifted&&!m.shift){m.doubleShift=user.tickCount-m.lastShift<=7;m.lastShift=user.tickCount;}if(!shifted)m.doubleShift=false;m.shift=shifted;
        if(!user.level().isClientSide()){
            boolean walking=m.waterContact && HamonService.hasHamon(user) && user.isAlive();m.waterContact=false;
            if(walking!=isWalkingOnLiquid(user))user.setAttached(LIQUID_WALKING,walking);
        }
    }
    public static boolean liquidWalking(LivingEntity user,FluidState fluid){
        if(!HamonService.hasHamon(user) || !HamonService.state(user).hasSkill("liquid_walking") || fluid.isEmpty() || HamonService.state(user).energy()<=0)return false;
        var m=MOTION.computeIfAbsent(user.getUUID(),id->new Motion());if(m.doubleShift || fluid.is(FluidTags.WATER)&&user.isOnFire())return false;
        // Modern grounding also searches support blocks; only transition when needed.
        if(!user.onGround())user.setOnGround(true);if(!user.level().isClientSide())m.waterContact=true;
        if(!user.level().isClientSide() && m.waterTick!=user.tickCount){float cost=m.waterTick==user.tickCount-1?1:50;HamonService.consumeEnergy(user,cost,"liquid_walking");
            if(fluid.is(FluidTags.LAVA) && !user.fireImmune()){
                var frost=user.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FROST_WALKER);
                if(EnchantmentHelper.getItemEnchantmentLevel(frost,user.getItemBySlot(EquipmentSlot.FEET))==0 && user.level() instanceof net.minecraft.server.level.ServerLevel level)user.hurtServer(level,user.damageSources().hotFloor(),1);
            }m.waterTick=user.tickCount;
        }return true;
    }
    static void snapToWall(ServerPlayer user,Vec3 toward,net.minecraft.core.Direction face){
        double low=0,high=1;
        for(int i=0;i<30;i++){double middle=(low+high)/2;if(user.level().noCollision(user,user.getBoundingBox().move(toward.scale(middle))))low=middle;else high=middle;}
        Vec3 position=user.position().add(toward.scale(low)).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(user.getBbWidth()*.15));
        user.teleportTo(position.x,position.y,position.z);
    }
    static void tickClimbing(ServerPlayer user,HamonActionService.Runtime r){
        if(!HamonService.state(user).hasSkill("wall_climbing") || !user.getMainHandItem().isEmpty() && !user.getMainHandItem().is(HamonItems.GLOVES) && !user.getMainHandItem().is(HamonItems.BUBBLE_GLOVES) || !user.getOffhandItem().isEmpty() && !user.getOffhandItem().is(HamonItems.GLOVES) && !user.getOffhandItem().is(HamonItems.BUBBLE_GLOVES)){r.wallClimbing=false;return;}
        Vec3 grip=new Vec3(0,0,.5).yRot((float)Math.toRadians(r.wallYaw));if(user.level().noCollision(user,user.getBoundingBox().move(grip))){r.wallClimbing=false;return;}
        boolean moving=user.getLastClientInput().forward()||user.getLastClientInput().backward()||user.getLastClientInput().left()||user.getLastClientInput().right();
        if(!HamonService.isBreathingHeld(user)){float cost=moving?10:2.5F;float points=Math.min(cost,HamonService.state(user).energy()*HamonService.efficiency(user,cost,"wall_climbing"));if(HamonService.state(user).energy()<cost){r.wallClimbing=false;return;}HamonService.consumeEnergy(user,cost,"wall_climbing");if(moving)HamonService.gainActionPoints(user,false,points);}
        user.fallDistance=0;
    }
    public static boolean travel(LivingEntity entity,Vec3 input,boolean jumping){
        if(!(entity instanceof Player user) || !user.isLocalPlayer() || !HamonActionService.state(user).wallClimbing() || user.isSpectator())return false;
        float yaw=(float)Math.toRadians(HamonActionService.state(user).wallYaw());Vec3 grip=new Vec3(0,0,.5).yRot(yaw);
        if(user.level().noCollision(user,user.getBoundingBox().move(grip)))return false;
        AABB upper=user.getBoundingBox().contract(0,-user.getBbHeight()*.5,0);
        boolean top=user.level().noCollision(user,upper.move(0,upper.getYsize(),0).move(grip));
        if(jumping){if(top){user.move(MoverType.SELF,new Vec3(0,user.getBbHeight(),0));user.setDeltaMovement(new Vec3(0,0,.1).yRot(yaw));}return false;}
        var h=HamonService.state(user);double speed=(1.2+h.breathingLevel()*.004+h.controlLevel()*.00667)*HamonService.efficiency(user,h.breathStability()/2,"wall_climbing")*user.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 movement=new Vec3(input.x*.5,input.z>0?input.z:input.z*.5,0);if(movement.lengthSqr()>1)movement=movement.normalize();
        if(input.x!=0&&input.z!=0)speed*=input.z>0?1/Math.sqrt(1.25):.5/Math.sqrt(.5);
        movement=movement.scale(speed).yRot(yaw);Vec3 horizontal=movement.multiply(1,0,1);
        if(movement.y<0){if(user.onGround())return false;if(user.level().noCollision(user,upper.move(0,-upper.getYsize()+movement.y,0).move(grip)))movement=horizontal;}
        if(top && movement.y>0)movement=horizontal;
        if(horizontal.lengthSqr()>1E-8 && user.level().noCollision(user,upper.move(horizontal.normalize().scale(horizontal.length()+user.getBbWidth()+.1)).move(grip)))movement=new Vec3(0,movement.y,0);
        user.fallDistance=0;user.setDeltaMovement(movement);user.move(MoverType.SELF,movement);return true;
    }
    private HamonMovement(){}
}
