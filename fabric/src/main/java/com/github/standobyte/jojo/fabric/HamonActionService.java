package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import com.github.standobyte.jojo.core.StandStatFormulas;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Authoritative Forge Hamon actions, charge phases and continuous player actions. */
public final class HamonActionService {
    public static final AttachmentType<HamonActionState> STATE=AttachmentRegistry.<HamonActionState>builder().initializer(()->HamonActionState.EMPTY)
            .syncWith(HamonActionState.STREAM_CODEC,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("hamon_actions"));
    private static final Identifier SPEED=RipplesFabric.id("hamon_action_speed"),REBUFF_RESIST=RipplesFabric.id("hamon_rebuff_resistance");
    private static final Map<UUID,Runtime> RUNTIME=new HashMap<>();
    static final class Runtime {
        String held="",continuous="",lastUsed="";int heldTicks,continuousTicks,lease,regen=-1,rebuffPhase,rebuffTick,rebuffCooldown=100;
        float spent;boolean protection,wallClimbing,didAttack,pointsGiven;float wallYaw;
        final Map<String,Integer>cooldowns=new HashMap<>();final Set<UUID>hit=new HashSet<>();List<Integer>detected=List.of();
    }
    public record Target(LivingEntity entity,BlockHitResult block){}
    public static HamonActionState state(LivingEntity user){return user.getAttachedOrCreate(STATE);}
    static Runtime runtime(LivingEntity user){return RUNTIME.computeIfAbsent(user.getUUID(),id->new Runtime());}
    public static boolean isHeld(LivingEntity e,String action){return state(e).held().equals(action);}
    public static void initialize(){
        HamonEntities.initialize();HamonEffects.initialize();HamonCharges.initialize();HamonMovement.initialize();HamonJosephChat.initialize();
        PayloadTypeRegistry.serverboundPlay().register(HamonActionPayload.TYPE,HamonActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HamonActionPayload.TYPE,(payload,ctx)->ctx.server().execute(()->handle(ctx.player(),payload)));
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers())tick(player);});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->allowDamage(entity,source,amount));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->clear(h.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player,origin,destination)->clear(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->clear(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->RUNTIME.clear());
    }
    public static void clear(LivingEntity user){var r=RUNTIME.remove(user.getUUID());if(r!=null){HamonService.setBreathingHeld(user,false);removeMovementModifier(user);}user.setAttached(STATE,HamonActionState.EMPTY);}
    private static void sync(LivingEntity u,Runtime r){u.setAttached(STATE,new HamonActionState(r.held,r.heldTicks,r.continuous,r.continuousTicks,r.spent,r.protection,r.wallClimbing,r.wallYaw,r.cooldowns,r.detected,r.lastUsed));}
    public static void handle(ServerPlayer user,HamonActionPayload packet){
        if(packet.operation()==HamonActionPayload.Operation.CANCEL){release(user,false);runtime(user).wallClimbing=false;stopContinuous(user,runtime(user));sync(user,runtime(user));return;}
        if(packet.operation()==HamonActionPayload.Operation.RELEASE){release(user,true);return;}
        if(packet.operation()==HamonActionPayload.Operation.KEEP){var r=runtime(user);if(r.held.equals(HamonActionCatalog.normalize(packet.action())))r.lease=0;return;}
        if(packet.operation()==HamonActionPayload.Operation.LEAP){var r=runtime(user);if(r.wallClimbing){r.wallClimbing=false;sync(user,r);}else leap(user);return;}
        start(user,packet.action());
    }
    public static boolean start(ServerPlayer user,String requested){
        String id=HamonActionCatalog.normalize(requested);var r=runtime(user);var target=target(user);
        if(id==null || !HamonActionCatalog.unlocked(HamonService.state(user),id) || !eligible(user))return false;
        if(id.equals("hamon_overdrive") && HamonService.state(user).hasSkill("metal_silver_overdrive")){
            if(weapon(user.getMainHandItem()))id="jonathan_metal_silver_overdrive_weapon";
            else if(target.entity()!=null && metalMultiplier(target.entity())>1)id="jonathan_metal_silver_overdrive";
        }
        if(id.equals("hamon_plant_infusion") && HamonService.state(user).hasSkill("animal_infusion"))id="hamon_organism_infusion";
        // Plant Infusion has the original BLOCK target requirement; an entity in front is not an organism target.
        if(id.equals("hamon_plant_infusion"))target=new Target(null,target.block());
        if(id.equals("joseph_rebuff_overdrive") && r.continuous.equals(id) && r.rebuffPhase==0){r.rebuffCooldown=(int)(100*Math.pow(r.rebuffTick/14D,2));stopContinuous(user,r);sync(user,r);return true;}
        if(!r.continuous.isEmpty() || !r.held.isEmpty() || r.cooldowns.getOrDefault(id,0)>0)return false;
        var action=HamonActionCatalog.get(id);if(!requirements(user,action,target))return false;
        if(action.held()){
            r.held=id;r.heldTicks=0;r.lease=0;r.spent=0;r.regen=-1;
            if(id.equals("hamon_breath"))HamonService.setBreathingHeld(user,true);
            if(id.equals("hamon_projectile_shield"))user.level().addFreshEntity(new HamonProjectileShieldEntity(user));
            if(id.equals("caesar_bubble_barrier")){var barrier=new HamonProjectileEntity(HamonEntities.BARRIER,user).barrier((int)(100*efficiency(user,0,true,"bubble_barrier")));user.level().addFreshEntity(barrier);}
            r.lastUsed=id;sound(user,id);sync(user,r);return true;
        }
        boolean performed=perform(user,action,target,r);sync(user,r);return performed;
    }
    private static boolean eligible(LivingEntity user){return user.isAlive() && !user.isSpectator() && HamonService.hasHamon(user) && !HamonService.isMeditating(user)
            && HamonService.bloodstreamEfficiency(user)>0 && !TimeStopService.isFrozen(user) && !user.hasEffect(VampireCombatService.STUN) && !user.hasEffect(HamonEffects.SHOCK);}
    private static boolean free(ItemStack stack){String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();return stack.isEmpty() || id.equals("gloves") || id.equals("bubble_gloves");}
    public static boolean weapon(ItemStack s){return !s.isEmpty() && !free(s) && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES) || s.is(Items.TRIDENT) || s.has(DataComponents.WEAPON));}
    private static boolean hands(LivingEntity user,HamonActionCatalog.Definition a){return (a.freeHands()&1)==0 || free(user.getMainHandItem()) ? (a.freeHands()&2)==0 || free(user.getOffhandItem()):false;}
    private static boolean requirements(ServerPlayer user,HamonActionCatalog.Definition a,Target target){
        String id=a.id();
        boolean heldLeaves=id.equals("hamon_life_magnetism") && (HamonItems.isLeaves(user.getMainHandItem()) || HamonItems.isLeaves(user.getOffhandItem()));
        if(!hands(user,a) && !heldLeaves)return fail(user,"hands");
        if(id.equals("jonathan_metal_silver_overdrive_weapon") && !weapon(user.getMainHandItem()))return false;
        if(id.equals("hamon_breath") && user.getAirSupply()<user.getMaxAirSupply())return fail(user,"no_air");
        if(id.equals("hamon_turquoise_blue_overdrive") && !user.isInWater())return fail(user,"underwater");
        if(id.equals("zeppeli_sendo_wave_kick") && !user.onGround())return false;
        if((id.equals("hamon_protection") || id.equals("hamon_shock") || id.equals("hamon_sunlight_yellow_overdrive") || id.equals("jonathan_scarlet_overdrive")) && HamonService.state(user).energy()<=0 && !runtime(user).held.equals(id))return fail(user,"some_energy");
        if(id.startsWith("caesar_bubble_") && !HamonItems.soapHasAmmo(user,1))return fail(user,"soap");
        if(id.equals("zeppeli_hamon_cutter") && cutterItem(user).isEmpty())return fail(user,"potion");
        if(id.equals("hamon_hypnosis") && !HamonCharges.canHypnotize(target.entity(),user))return fail(user,"hypnosis");
        if(id.equals("hamon_shock") && !shockable(target.entity()))return fail(user,"living_mob_shock");
        if(id.equals("hamon_overdrive") || id.startsWith("jonathan_metal_silver_overdrive"))return target.entity()!=null;
        if(id.equals("hamon_plant_infusion") && target.block()!=null && user.level().getBlockState(target.block().getBlockPos()).getBlock() instanceof TurtleEggBlock)return fail(user,"turtle_egg");
        if(id.equals("hamon_plant_infusion") || id.equals("hamon_organism_infusion")){
            if(id.equals("hamon_organism_infusion") && target.entity()!=null)return living(target.entity()) || fail(user,"living_mob");
            return target.block()!=null && (HamonCharges.livingBlock(user.level().getBlockState(target.block().getBlockPos())) || fail(user,"living_plant"));
        }
        return true;
    }
    private static boolean fail(ServerPlayer user,String suffix){user.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("jojo.message.action_condition."+suffix));return false;}
    public static Target target(LivingEntity user){
        Vec3 start=user.getEyePosition(),end=start.add(user.getLookAngle().scale(10));
        BlockHitResult block=user.level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,user));
        Vec3 entityEnd=start.add(user.getLookAngle().scale(8));if(block.getType()!=HitResult.Type.MISS && block.getLocation().distanceToSqr(start)<64)entityEnd=block.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(user.level(),user,start,entityEnd,user.getBoundingBox().expandTowards(user.getLookAngle().scale(8)).inflate(1),e->e instanceof LivingEntity && e!=user && e.isAlive() && e.isPickable() && !e.isSpectator(),.3F);
        return new Target(hit!=null?(LivingEntity)hit.getEntity():null,block.getType()==HitResult.Type.BLOCK?block:null);
    }
    public static float efficiency(LivingEntity u,float cost,boolean swing,String skill){float e=HamonService.efficiency(u,cost,skill);if(swing && u instanceof Player p){float scale=p.getAttackStrengthScale(1);e*=.2F+scale*scale*.8F;}return e;}
    public static boolean damage(LivingEntity target,LivingEntity user,float amount){return amount>0 && target.level() instanceof ServerLevel level && VampireHamon.damage(level,target,user,amount);}
    public static void knockback(LivingEntity target,LivingEntity user,float amount,Vec3 direction){var src=target.damageSources().mobAttack(user);target.knockback(amount,-direction.x,-direction.z,src,amount);target.syncVelocity=true;}
    public static void knockback(LivingEntity target,LivingEntity user,float amount){knockback(target,user,amount,target.position().subtract(user.position()));}
    public static float unarmedDamage(LivingEntity user){float value=(float)user.getAttributeValue(Attributes.ATTACK_DAMAGE);var modifiers=user.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS);if(modifiers!=null)for(var entry:modifiers.modifiers())if(entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().operation()==AttributeModifier.Operation.ADD_VALUE)value-=entry.modifier().amount();return Math.max(value,0);}
    public static boolean living(LivingEntity entity){return entity!=null && !VampireHamon.affected(entity) && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand) && !(entity instanceof net.minecraft.world.entity.animal.golem.AbstractGolem);}
    private static boolean shockable(LivingEntity e){return living(e) && !e.hasEffect(VampireCombatService.STUN) && !e.hasEffect(HamonEffects.SHOCK) && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("hamon_master");}
    private static float metalMultiplier(LivingEntity e){float f=1;for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))if(!e.getItemBySlot(slot).isEmpty())f+=.2F;if(weapon(e.getMainHandItem())||weapon(e.getOffhandItem()))f+=.2F;return f;}
    private static void points(LivingEntity u,boolean strength,float cost,float eff){HamonService.gainActionPoints(u,strength,Math.min(cost,HamonService.state(u).energy())*eff);}
    private static void pay(LivingEntity u,HamonActionCatalog.Definition a,float cost){HamonService.consumeEnergy(u,cost,a.skill());}
    private static void melee(ServerPlayer user,LivingEntity target){user.attack(target);user.resetAttackStrengthTicker();}
    private static void swing(LivingEntity user,boolean off){user.swing(off?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);user.level().playSound(null,user.getX(),user.getEyeY(),user.getZ(),JojoSounds.get("hamon_syo_swing"),user.getSoundSource(),1,1);}
    private static void sound(LivingEntity user,String action){
        String t=HamonService.state(user).technique(),id="";
        switch(action){
            case "hamon_breath"->{if(HamonService.state(user).energy()>0)return;if(t.isEmpty()){user.level().playSound(null,user.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_BREATH,user.getSoundSource(),1,1);return;}id=t+"_breath";}
            case "hamon_sendo_overdrive"->{if(t.equals("jonathan"))id="jonathan_sendo_overdrive";}
            case "hamon_sunlight_yellow_overdrive"->{if(Set.of("jonathan","zeppeli","joseph","caesar").contains(t))id=t+"_sunlight_yellow_overdrive";}
            case "hamon_zoom_punch"->{if(Set.of("jonathan","zeppeli","joseph").contains(t))id=t+"_zoom_punch";}
            case "hamon_life_magnetism"->{if(t.equals("zeppeli"))id="zeppeli_life_magnetism_overdrive";}
            case "hamon_projectile_shield"->{if(t.equals("joseph"))id="joseph_barrier";}
            case "jonathan_scarlet_overdrive","jonathan_overdrive_barrage","zeppeli_hamon_cutter","zeppeli_sendo_wave_kick","zeppeli_tornado_overdrive","caesar_bubble_launcher","caesar_bubble_barrier","caesar_bubble_cutter","caesar_bubble_cutter_gliding"->id=action;
            case "jonathan_syo_barrage"->id=runtime(user).held.isEmpty()?"jonathan_syo_barrage":"jonathan_syo_barrage_start";
        }
        if(!id.isEmpty())user.level().playSound(null,user.getX(),user.getEyeY(),user.getZ(),JojoSounds.get(id),user.getSoundSource(),1,1);
    }
    private static boolean perform(ServerPlayer user,HamonActionCatalog.Definition a,Target target,Runtime r){
        var h=HamonService.state(user);float cost=a.energyCost(),e=efficiency(user,cost,true,a.skill());boolean spend=true;
        switch(a.id()){
            case "hamon_overdrive","jonathan_metal_silver_overdrive","jonathan_metal_silver_overdrive_weapon"->{if(target.entity()==null)return false;float multiplier=a.id().equals("jonathan_metal_silver_overdrive")?metalMultiplier(target.entity()):1;if(damage(target.entity(),user,2*e*multiplier))points(user,true,cost,e);melee(user,target.entity());}
            case "hamon_overdrive_beat","hamon_shock","joseph_rebuff_overdrive","hamon_sunlight_yellow_overdrive","jonathan_scarlet_overdrive","jonathan_syo_barrage"->{r.continuous=a.id();r.continuousTicks=0;r.didAttack=false;r.rebuffPhase=0;r.rebuffTick=0;r.rebuffCooldown=100;spend=false;
                if(a.id().equals("joseph_rebuff_overdrive")){var attribute=user.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(attribute!=null)attribute.addOrUpdateTransientModifier(new AttributeModifier(REBUFF_RESIST,1,AttributeModifier.Operation.ADD_VALUE));}}
            case "hamon_sendo_overdrive"->{Vec3 eye=user.getEyePosition();var solid=user.level().clip(new ClipContext(eye,eye.add(user.getLookAngle().scale(10)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,user));if(solid.getType()==HitResult.Type.MISS)return false;target=new Target(null,solid);float eff=efficiency(user,cost,false,a.skill());float c=h.controlLevel()/60F;float arc=(float)Math.PI/4+Mth.clamp((r.heldTicks-1)/30F,0,1)*(float)Math.PI*7/4;
                var wave=new HamonSendoEntity(user,Vec3.atCenterOf(target.block().getBlockPos()),target.block().getDirection().getAxis(),(2+3*c)*eff,arc,2+(int)((2+Math.min(c*3,2))*eff),.75F*eff,Math.min(cost,h.energy())*eff);wave.setBlockTarget(target.block().getBlockPos(),target.block().getDirection());user.level().addFreshEntity(wave);swing(user,false);}
            case "hamon_turquoise_blue_overdrive"->{var wave=new HamonProjectileEntity(HamonEntities.TURQUOISE,user).configure(e,Math.min(cost,h.energy())*e,30+(int)(70*h.controlLevel()/60F),1+2.5F*h.controlLevel()/60F*e);wave.shootFrom(user,1.5F,0);user.level().addFreshEntity(wave);}
            case "hamon_zoom_punch"->{float length=4+(4+.1F*h.controlLevel())*e;float speed=2*length/14*(.4F+.6F*e);user.level().addFreshEntity(new HamonProjectileEntity(HamonEntities.ZOOM,user).configure(.7F,Math.min(cost,h.energy())*e,14,.15F).zoom(speed,h.energy()<=0));}
            case "hamon_speed_boost"->{float eff=efficiency(user,cost,false,a.skill()),str=h.controlLevel()/60F*eff;int amp=(int)Math.floor(str*1.5)+(h.hasSkill("afterimages")?1:0),duration=20+(int)Math.floor(180*str);
                if(eff==1 && h.hasSkill("afterimages"))afterimages(user,Math.min((int)(str*7/1.5F),7),duration);
                if(!user.hasEffect(MobEffects.SPEED))HamonService.gainActionPoints(user,false,cost);user.addEffect(new MobEffectInstance(MobEffects.SPEED,duration,amp));user.addEffect(new MobEffectInstance(MobEffects.HASTE,duration,amp));}
            case "hamon_plant_infusion","hamon_organism_infusion"->{boolean organism=a.id().equals("hamon_organism_infusion") && target.entity()!=null;
                if(organism && !living(target.entity()))return false;
                cost=organism?1000:200;float eff=efficiency(user,cost,true,a.skill());int ticks=100+(int)Math.floor(1100*h.strengthLevel()/60F*eff*eff);float charge=h.damageMultiplier()*eff;
                if(organism)HamonCharges.charge(target.entity(),user,charge,ticks,cost);
                else if(target.block()!=null){HamonCharges.chargeBlock(user,target.block().getBlockPos(),charge,ticks,cost);if(h.hasSkill("hamon_spread"))for(var d:Direction.values())HamonCharges.chargeBlock(user,target.block().getBlockPos().relative(d),charge,ticks,cost);}else return false;}
            case "hamon_protection"->{r.protection=!r.protection;spend=false;}
            case "hamon_wall_climbing"->{spend=false;if(r.wallClimbing)r.wallClimbing=false;else if(target.block()!=null && target.block().getDirection().getAxis()!=Direction.Axis.Y){var face=target.block().getDirection();Vec3 grip=Vec3.atLowerCornerOf(face.getOpposite().getUnitVec3i()).scale(.5);if(user.level().noCollision(user,user.getBoundingBox().move(grip)))return false;HamonMovement.snapToWall(user,grip,face);r.wallClimbing=true;r.wallYaw=180-face.toYRot();}else return false;}
            case "hamon_life_magnetism"->{if(!HamonItems.lifeMagnetism(user,target.block(),h.energy()))return false;cost=HamonItems.GLIDER_MAX_ENERGY;HamonService.gainActionPoints(user,false,cost);}
            case "hamon_hypnosis"->{if(target.entity()==null)return false;HamonCharges.hypnotize(target.entity(),user,(int)(Math.pow(h.controlLevel()/60F,2)*24000));}
            case "zeppeli_sendo_wave_kick"->{if(!user.onGround())return false;r.continuous=a.id();r.continuousTicks=0;r.hit.clear();r.pointsGiven=false;r.spent=Math.min(cost,h.energy())*e;Vec3 leap=Vec3.directionFromRotation(Mth.clamp(user.getXRot(),-45,-18),user.getYRot()).scale(1+user.getAttributeValue(Attributes.MOVEMENT_SPEED)*5);user.setDeltaMovement(leap.multiply(1,.5,1));user.setOnGround(false);user.syncVelocity=true;}
            case "zeppeli_hamon_cutter"->{ItemStack item=cutterItem(user);if(item.isEmpty())return false;for(int i=0;i<8;i++){var cutter=new HamonProjectileEntity(HamonEntities.CUTTER,user).configure(.075F,50,100,.1F).potion(item);cutter.shootFrom(user,1.35F+user.getRandom().nextFloat()*.3F,10);user.level().addFreshEntity(cutter);}if(!user.isCreative()){item.shrink(1);giveBottle(user);}}
            case "caesar_bubble_cutter","caesar_bubble_cutter_gliding"->{if(HamonItems.consumeSoap(user,20)==HamonItems.SoapSource.NONE)return false;boolean gliding=a.id().endsWith("gliding");for(int i=0;i<(gliding?4:8);i++){var cutter=new HamonProjectileEntity(HamonEntities.BUBBLE_CUTTER,user).configure(.3F,cost/10,100,.3F).gliding(gliding);cutter.shootFrom(user,1.35F+user.getRandom().nextFloat()*.3F,gliding?2:8);user.level().addFreshEntity(cutter);}}
            case "caesar_bubble_barrier"->{HamonItems.consumeSoap(user,50);}
            default->{return false;}
        }
        r.lastUsed=a.id();if(spend){pay(user,a,cost);if(a.cooldown()>0)r.cooldowns.put(a.id(),a.cooldown());}sound(user,a.id());return true;
    }
    private static ItemStack cutterItem(LivingEntity u){for(var stack:List.of(u.getMainHandItem(),u.getOffhandItem()))if(stack.getItem() instanceof PotionItem || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("soap"))return stack;return ItemStack.EMPTY;}
    private static void giveBottle(ServerPlayer user){if(!user.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))user.spawnAtLocation(user.level(),new ItemStack(Items.GLASS_BOTTLE));}
    public static void release(ServerPlayer user,boolean fire){var r=runtime(user);if(r.held.isEmpty())return;var a=HamonActionCatalog.get(r.held);boolean charged=a!=null && r.heldTicks>=a.holdTicks();
        if(a!=null && (fire && charged && !a.holdOnly() || a.id().equals("hamon_sendo_overdrive") && fire))perform(user,a,target(user),r);
        else if(a!=null && (a.id().equals("hamon_sunlight_yellow_overdrive") || a.id().equals("jonathan_scarlet_overdrive")))HamonService.energy(user,HamonService.state(user).energy()+r.spent);
        r.held="";r.heldTicks=0;r.regen=-1;r.detected=List.of();HamonService.setBreathingHeld(user,false);sync(user,r);
    }
    public static void tick(ServerPlayer user){
        if(!HamonService.hasHamon(user)){if(RUNTIME.containsKey(user.getUUID()))clear(user);return;}
        var r=runtime(user);if(TimeStopService.isFrozen(user))return;
        r.cooldowns.replaceAll((id,n)->n-1);r.cooldowns.values().removeIf(n->n<=0);
        if(!eligible(user)){if(!r.held.isEmpty())release(user,false);if(!r.continuous.isEmpty())stopContinuous(user,r);r.wallClimbing=false;}
        if(!r.held.isEmpty()){
            if(++r.lease>15){release(user,false);}else{
                var a=HamonActionCatalog.get(r.held);var target=target(user);if(a==null || !requirements(user,a,target)){release(user,false);}else{
                    r.heldTicks++;float cost=a.holdEnergyCost();if(a.id().equals("hamon_sunlight_yellow_overdrive") || a.id().equals("jonathan_scarlet_overdrive"))cost=Math.min(HamonService.state(user).maxBreathStability()/a.chargeTicks(),HamonService.state(user).energy());
                    else if(a.id().equals("jonathan_syo_barrage"))cost=HamonService.state(user).breathStability()/100;
                    holdTick(user,a,target,r,cost);float before=HamonService.state(user).energy();pay(user,a,cost);r.spent+=user.isCreative()?cost:Math.max(0,before-HamonService.state(user).energy());
                    if(!a.holdOnly() && !a.id().equals("hamon_sendo_overdrive") && !a.id().equals("hamon_sunlight_yellow_overdrive") && !a.id().equals("jonathan_scarlet_overdrive") && r.heldTicks>=a.holdTicks())release(user,true);
                }
            }
        }
        if(!r.continuous.isEmpty())continuousTick(user,r);
        if(HamonService.state(user).energy()<=0){r.protection=false;r.lastUsed="";}
        if(r.wallClimbing)HamonMovement.tickClimbing(user,r);
        updateMovementModifier(user,r);sync(user,r);
    }
    private static void holdTick(ServerPlayer user,HamonActionCatalog.Definition a,Target target,Runtime r,float cost){
        switch(a.id()){
            case "hamon_healing"->heal(user,target,r,cost);
            case "hamon_detector"->{if(r.heldTicks<160 || r.heldTicks%20==0){float control=HamonService.state(user).controlLevel()/60F*efficiency(user,cost,false,a.skill());double radius=Math.min(r.heldTicks*(control*.8+.2),8+control*24);var found=user.level().getEntitiesOfClass(LivingEntity.class,user.getBoundingBox().inflate(radius),e->e!=user && !(e instanceof net.minecraft.world.entity.animal.golem.AbstractGolem) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !(e instanceof StarPlatinumEntity stand && stand.owner()==user));r.detected=found.stream().map(Entity::getId).toList();if(!found.isEmpty())HamonService.gainActionPoints(user,false,Math.min(cost,HamonService.state(user).energy()));}}
            case "hamon_hypnosis"->{if(target.entity()!=null){target.entity().addEffect(new MobEffectInstance(HamonEffects.IMMOBILIZE,2,0,false,false));if(target.entity() instanceof Mob mob)mob.getNavigation().stop();}}
            case "jonathan_overdrive_barrage"->barrage(user,target,false,r);
            case "zeppeli_tornado_overdrive"->{user.fallDistance=0;Vec3 motion=user.getDeltaMovement();float damage=.6F;double gravity=user.getAttributeValue(Attributes.GRAVITY);if(motion.y < -gravity)damage*=(-motion.y/gravity)*.15F;boolean hit=false;for(var e:user.level().getEntitiesOfClass(LivingEntity.class,user.getBoundingBox().expandTowards(motion).inflate(1),e->e!=user))hit|=damage(e,user,damage);if(hit)HamonService.gainActionPoints(user,true,75);if(user.isShiftKeyDown())user.setDeltaMovement(0,motion.y<0?motion.y*1.05:0,0);}
            case "caesar_bubble_launcher"->{var soap=HamonItems.consumeSoap(user,1);if(soap==HamonItems.SoapSource.NONE)return;for(int i=0;i<(soap==HamonItems.SoapSource.BOTTLE?36:4);i++){var bubble=new HamonProjectileEntity(HamonEntities.BUBBLE,user);bubble.shootFrom(user,.1F+user.getRandom().nextFloat()*.5F,16);user.level().addFreshEntity(bubble);}}
        }
    }
    private static void continuousTick(ServerPlayer user,Runtime r){
        int tick=r.continuousTicks++;String id=r.continuous;var a=HamonActionCatalog.get(id);var target=target(user);var h=HamonService.state(user);
        switch(id){
            case "hamon_overdrive_beat"->{if(tick==2)swing(user,true);if(tick==5 && target.entity()!=null){float e=efficiency(user,1500,true,a.skill());if(hands(user,a) && damage(target.entity(),user,3*e)){knockback(target.entity(),user,1.25F);points(user,true,1500,e);pay(user,a,1500);}melee(user,target.entity());}if(tick>=8)stopContinuous(user,r);}
            case "hamon_sunlight_yellow_overdrive","jonathan_scarlet_overdrive"->{boolean scarlet=id.equals("jonathan_scarlet_overdrive");if(tick==1)swing(user,scarlet);if(tick==4 && target.entity()!=null){var victim=target.entity();float ratio=Math.min(r.spent/h.maxBreathStability(),1),e=efficiency(user,0,true,a.skill()),amount=(scarlet?2.5F+5*ratio:3.25F+6.75F*ratio)*e;int fire=scarlet?(int)Math.floor(2+8*h.strengthLevel()/60F*e):0;
                if(hands(user,a) && damage(victim,user,amount)){if(fire>0)victim.igniteForSeconds(fire);HamonService.gainActionPoints(user,true,h.maxBreathStability()*ratio*e);knockback(victim,user,scarlet?2:2.5F);HamonCharges.collision(victim,user,h.hasSkill("hamon_spread")?amount:0,scarlet?Math.max(20*fire/2,20):0);}melee(user,victim);}if(tick>=(scarlet?9:11))stopContinuous(user,r);}
            case "hamon_shock"->{if(tick==9 && shockable(target.entity())){float e=efficiency(user,0,false,a.skill()),ratio=h.energy()/Math.max(h.maxBreathStability(),1);int duration=(int)(20+(80*h.controlLevel()/60F+60*ratio)*e);target.entity().addEffect(new MobEffectInstance(HamonEffects.SHOCK,duration,(int)(h.strengthLevel()/60F*.05F*e),false,false,true));HamonService.gainActionPoints(user,false,h.energy()*e);HamonService.energy(user,0);}if(tick>=15)stopContinuous(user,r);}
            case "joseph_rebuff_overdrive"->{if(!hands(user,a)){stopContinuous(user,r);break;}if(r.rebuffPhase==0 && r.rebuffTick==11)swing(user,false);if(r.rebuffPhase==0 && r.rebuffTick>=14){r.rebuffPhase=1;r.rebuffTick=0;if(!r.didAttack && target.entity()!=null)rebuffPunch(user,target.entity(),r,false);}else if(r.rebuffPhase==1 && r.rebuffTick>=10){r.rebuffPhase=2;r.rebuffTick=0;}else if(r.rebuffPhase==2 && r.rebuffTick>=16)stopContinuous(user,r);r.rebuffTick++;}
            case "jonathan_syo_barrage"->{if(tick<70){barrage(user,target,true,r);if(target.entity()!=null && target.entity().getHealth()<2)r.continuousTicks=70;}else if(tick==70){if(target.entity()!=null){var victim=target.entity();victim.removeEffect(HamonEffects.IMMOBILIZE);float e=efficiency(user,0,false,a.skill());if(damage(victim,user,15*e)){knockback(victim,user,2);if(h.hasSkill("hamon_spread"))victim.addEffect(new MobEffectInstance(VampireHamon.SPREAD,200,3));}melee(user,victim);}swing(user,false);}else if(tick>=80)stopContinuous(user,r);}
            case "zeppeli_sendo_wave_kick"->{if(tick>0 && (user.onGround() || !user.level().getFluidState(user.blockPosition()).isEmpty())){stopContinuous(user,r);break;}Vec3 forward=Vec3.directionFromRotation(0,user.getYRot());Vec3 center=user.position().add(forward.scale(user.getBbWidth()*.75));AABB box=new AABB(center,center).inflate(user.getBbWidth()*1.25,.125,user.getBbWidth()*1.25).expandTowards(0,user.getBbHeight()/2,0);boolean hit=false;
                for(var victim:user.level().getEntitiesOfClass(LivingEntity.class,box,e->e!=user && e.isAlive()))if(r.hit.add(victim.getUUID())){boolean physical=StandDamage.hurt(user.level(),victim,user.damageSources().playerAttack(user),unarmedDamage(user));boolean hamon=damage(victim,user,3);if(physical||hamon){Vec3 direction=victim.position().subtract(user.position());float angle=60+user.getRandom().nextFloat()*30;direction=direction.yRot((float)Math.toRadians(angle*(user.getRandom().nextBoolean()?1:-1)));knockback(victim,user,.75F,direction);}hit|=hamon;}if(hit && !r.pointsGiven){HamonService.gainActionPoints(user,true,r.spent);r.pointsGiven=true;}user.fallDistance=0;}
        }
    }
    private static void barrage(ServerPlayer user,Target target,boolean sunlight,Runtime r){
        if(target.entity()!=null){var victim=target.entity();if(sunlight){victim.addEffect(new MobEffectInstance(HamonEffects.IMMOBILIZE,10,0,false,false));int inv=victim.damageCooldownTime;melee(user,victim);victim.damageCooldownTime=inv;}else{
            float amount=StandStatFormulas.getBarrageHitDamage(user.getAttributeValue(Attributes.ATTACK_DAMAGE)+8,0)*StandStatFormulas.getBarrageHitsPerSecond(user.getAttributeValue(Attributes.ATTACK_SPEED)+8)/20F;
            StandDamage.hurt(user.level(),victim,user.damageSources().playerAttack(user),amount);user.causeFoodExhaustion(.1F);
        }damage(victim,user,.1F);
        }else if(target.block()!=null){BlockPos pos=target.block().getBlockPos();var block=user.level().getBlockState(pos);if(!user.mayBuild() || !user.mayInteract(user.level(),pos))return;
            float duration=block.getDestroySpeed(user.level(),pos)/Math.max(user.getDestroySpeed(block),.0001F);if(user.isCreative())duration=0;else if(!user.hasCorrectToolForDrops(block))duration*=10F/3;
            if(duration>=0 && duration<=2.5*Math.sqrt(user.getAttributeValue(Attributes.ATTACK_DAMAGE))){user.level().destroyBlock(pos,!user.isCreative(),user);if(!sunlight)HamonService.gainActionPoints(user,true,70);}}
        if((r.heldTicks+r.continuousTicks)%2==0)user.swing((r.heldTicks+r.continuousTicks)%4==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
    }
    private static void stopContinuous(LivingEntity user,Runtime r){if(r.continuous.equals("joseph_rebuff_overdrive") && r.rebuffCooldown>0)r.cooldowns.put(r.continuous,r.rebuffCooldown);r.continuous="";r.continuousTicks=0;var attr=user.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(attr!=null)attr.removeModifier(REBUFF_RESIST);}
    private static void rebuffPunch(ServerPlayer user,LivingEntity victim,Runtime r,boolean counter){float e=efficiency(user,1200,true,"rebuff_overdrive");if(damage(victim,user,(counter?10:6)*e)){knockback(victim,user,counter?2:1);if(counter && HamonService.state(user).hasSkill("hamon_shock"))victim.addEffect(new MobEffectInstance(HamonEffects.SHOCK,50,0,false,false));pay(user,HamonActionCatalog.get(r.continuous),1200);HamonService.gainActionPoints(user,true,1200*e);}melee(user,victim);r.didAttack=true;r.rebuffCooldown=counter?0:50;swing(user,false);}
    public static boolean allowDamage(LivingEntity entity,DamageSource source,float amount){
        if(!(entity.level() instanceof ServerLevel))return true;
        if(HamonCharges.preventBlockDamage(entity,source,amount))return false;
        if(HamonCharges.snakeMuffler(entity,source,amount))return false;
        var r=RUNTIME.get(entity.getUUID());if(r==null)return true;
        if(r.held.equals("hamon_hypnosis") && entity instanceof ServerPlayer player)release(player,false);
        boolean melee=source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity()==source.getEntity();
        if(r.continuous.equals("zeppeli_sendo_wave_kick") && melee)return false;
        if(r.continuous.equals("joseph_rebuff_overdrive") && entity instanceof ServerPlayer user){
            if(r.rebuffPhase==1)return false;
            if(melee && !(source.getDirectEntity() instanceof StarPlatinumEntity) && source.getDirectEntity() instanceof LivingEntity attacker && attacker.position().subtract(user.position()).dot(user.getLookAngle())>0){
                float e=efficiency(user,1200,true,"rebuff_overdrive");if(r.rebuffPhase==0 && r.rebuffTick>=7 && (e==1 || e>=amount/user.getMaxHealth())){rebuffPunch(user,attacker,r,true);r.rebuffPhase=1;r.rebuffTick=0;sync(user,r);return false;}stopContinuous(user,r);sync(user,r);
            }
        }return true;
    }
    public static float reduceDamage(LivingEntity user,DamageSource source,float amount){var r=RUNTIME.get(user.getUUID());if(source.getDirectEntity()==null || source.is(DamageTypeTags.BYPASSES_ARMOR))return amount;if(user instanceof HamonMasterEntity)return 0;if(r==null || !HamonService.hasHamon(user))return amount;
        if(!r.protection && !r.continuous.equals("joseph_rebuff_overdrive"))return amount;
        float cost=amount*75,e=HamonService.efficiency(user,cost,"protection"),reduction=Mth.clamp((.4F+.2F*HamonService.state(user).controlLevel()/60F)*e,0,1);points(user,false,cost,e);HamonService.consumeEnergy(user,cost,"protection");if(reduction>0)VampireHamonParticles.emit(user,amount*reduction*.25F);return amount*(1-reduction);
    }
    private static void leap(ServerPlayer user){if(!eligible(user) || !HamonService.state(user).hasSkill("jump") || !(user.onGround() || user.getVehicle()!=null && user.getVehicle().onGround()))return;var r=runtime(user);if(r.cooldowns.getOrDefault("leap",0)>0 || !user.isCreative() && HamonService.state(user).energy()<250)return;
        HamonService.consumeResourceEnergy(user,250);HamonService.gainActionPoints(user,false,250);float strength=HamonService.state(user).hasSkill("afterimages")?1.5F:1.4F;
        Entity leaper=user.getVehicle()==null?user:user.getVehicle();Vec3 direction=Vec3.directionFromRotation(Math.min(leaper.getXRot(),-30F),leaper.getYRot()).scale(strength);
        leaper.setDeltaMovement(direction.x,direction.y*.5,direction.z);leaper.setOnGround(false);if(leaper instanceof LivingEntity living)living.setJumping(true);leaper.needsSync=true;leaper.syncVelocity=true;user.setShiftKeyDown(false);
        var ground=user.level().getBlockState(user.getOnPos());if(!ground.isAir()){
            user.level().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,ground),user.getX(),user.getY(),user.getZ(),(int)(150*Math.min(.2F+strength/3,2.5F)),0,0,0,.15);
            if(!user.isSilent()){var sound=ground.getSoundType();user.level().playSound(null,user.blockPosition(),sound.getBreakSound(),user.getSoundSource(),sound.getVolume()*.25F*strength,sound.getPitch()*.75F);}
        }r.cooldowns.put("leap",user.isCreative()?0:20);sync(user,r);
    }
    private static void updateMovementModifier(LivingEntity u,Runtime r){var attr=u.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;String id=!r.continuous.isEmpty()?r.continuous:r.held;var action=HamonActionCatalog.get(id);float multiplier=action==null?1:action.walkSpeed();if(multiplier<1)attr.addOrUpdateTransientModifier(new AttributeModifier(SPEED,multiplier-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));else attr.removeModifier(SPEED);}
    private static void removeMovementModifier(LivingEntity u){for(var pair:List.of(Attributes.MOVEMENT_SPEED,Attributes.KNOCKBACK_RESISTANCE)){var a=u.getAttribute(pair);if(a!=null){a.removeModifier(SPEED);a.removeModifier(REBUFF_RESIST);}}}
    private static void heal(ServerPlayer user,Target target,Runtime r,float cost){
        var h=HamonService.state(user);float c=h.controlLevel()/60F,e=efficiency(user,cost,false,"healing");if(e<=0)return;
        LivingEntity patient=user.isShiftKeyDown() && h.hasSkill("healing_touch") && living(target.entity())?target.entity():user;
        float q=3*c+4*(e-.75F)-1;int maxLevel=Mth.clamp((int)q,0,2),maxTicks=(int)Math.max((50+50*e)*(1+c),51);
        float rate=.75F+.75F*c;int inc=(int)(rate*r.heldTicks)-(int)(rate*(r.heldTicks-1));var old=patient.getEffect(MobEffects.REGENERATION);int amp=-1,duration=-1;
        if(old==null){amp=0;duration=51;}else if(old.getDuration()<maxTicks || old.getAmplifier()<maxLevel){if(r.regen<0)r.regen=old.getDuration();duration=Math.min(r.regen+inc,maxTicks);int give=(int)((float)duration/maxTicks*q);amp=Mth.clamp(old.getAmplifier(),give,maxLevel);}
        if(amp>=0 && duration>0){r.regen=duration;duration=alignedDuration(old,duration,amp,50);patient.addEffect(new MobEffectInstance(MobEffects.REGENERATION,duration,amp,false,false,true));}
        boolean harmful=reduceEffect(patient,VampireBlood.BLEEDING,r.heldTicks,inc,3200/maxTicks,0);
        if(h.hasSkill("expel_venom")){harmful|=reduceEffect(patient,MobEffects.POISON,r.heldTicks,inc,3200/maxTicks,25);harmful|=reduceEffect(patient,MobEffects.WITHER,r.heldTicks,inc,3200/maxTicks,40);harmful|=reduceEffect(patient,MobEffects.HUNGER,r.heldTicks,inc,3200/maxTicks,0);harmful|=reduceEffect(patient,MobEffects.NAUSEA,r.heldTicks,inc,3200/maxTicks,0);}
        if(h.hasSkill("plants_growth") && target.block()!=null){BlockPos pos=target.block().getBlockPos();if(BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL),user.level(),pos))user.level().levelEvent(2005,pos,0);else if(BoneMealItem.growWaterPlant(new ItemStack(Items.BONE_MEAL),user.level(),pos.relative(target.block().getDirection()),target.block().getDirection()))user.level().levelEvent(2005,pos,0);}
        float points=Math.min(cost,h.energy());if(old!=null && old.getAmplifier()==maxLevel && r.regen>=maxTicks)points*=Math.max(20/cost,2);if(patient.getHealth()<patient.getMaxHealth() || harmful)points*=4;if(patient!=user)points*=2;HamonService.gainActionPoints(user,false,points);
    }
    public static int alignedDuration(MobEffectInstance old,int duration,int level,int gap){if(old!=null && level<31-Integer.numberOfLeadingZeros(gap)){int oldGap=gap>>old.getAmplifier();if(oldGap>0){int applies=old.getDuration()%oldGap,newGap=gap>>level,newApplies=newGap>0?duration%newGap:0;if(newApplies<applies){int newDuration=duration+applies-newApplies;while(newDuration>duration)newDuration-=newGap;if(newDuration>0)duration=newDuration;}else duration-=newApplies-applies;}}return duration;}
    private static boolean reduceEffect(LivingEntity e,Holder<MobEffect> type,int held,int decrease,int every,int gap){var old=e.getEffect(type);if(old==null)return false;int amp=old.getAmplifier();if(amp>0 && held>0 && held%Math.max(every,1)==0)amp--;if(amp==0)decrease*=2;int duration=old.getDuration()-decrease;if(gap>0 && duration>0)duration=alignedDuration(old,duration,amp,gap);e.removeEffect(type);if(duration>0)e.addEffect(new MobEffectInstance(type,duration,amp,old.isAmbient(),old.isVisible(),old.showIcon()));return true;}
    private static void afterimages(LivingEntity user,int count,int duration){HamonAfterimageEntity.addAfterimages(user,count,duration);}
    private HamonActionService(){}
}
