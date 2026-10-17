package com.github.standobyte.jojo.fabric;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** Source capability adapters for infused plants, mobs, drops and vanilla projectiles. */
public final class HamonCharges {
    public static final AttachmentType<HamonCharge> CHARGE=AttachmentRegistry.<HamonCharge>builder().persistent(HamonCharge.CODEC).syncWith(HamonCharge.STREAM_CODEC,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("hamon_charge"));
    private static final AttachmentType<String> PRE_OWNER=AttachmentRegistry.<String>builder().persistent(Codec.STRING).buildAndRegister(RipplesFabric.id("pre_hypnosis_owner"));
    private static final Map<LivingEntity,Impact> IMPACTS=new IdentityHashMap<>();
    private record Impact(ServerPlayer owner,float damage,int fire,Set<UUID>hit,Vec3 direction){}
    private static final Map<ServerLevel,Projectile> EGGS=new IdentityHashMap<>();
    public static boolean isCharged(LivingEntity entity){var c=entity.getAttached(CHARGE);return c!=null && c.remaining()>=0;}
    public static void initialize(){
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{if(!entity.hasAttached(CHARGE))chargeNew(entity);});
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(!world.isClientSide() && hand==net.minecraft.world.InteractionHand.MAIN_HAND && !player.isSpectator() && (player.getMainHandItem().isEmpty() || player.getMainHandItem().is(HamonItems.GLOVES) || player.getMainHandItem().is(HamonItems.BUBBLE_GLOVES)) && world.getBlockState(hit.getBlockPos()).is(Blocks.TRIPWIRE) && ropeTrap(player,hit.getBlockPos()))return InteractionResult.SUCCESS;return InteractionResult.PASS;});
        ServerTickEvents.END_SERVER_TICK.register(s->EGGS.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{IMPACTS.clear();EGGS.clear();});
    }
    public static void charge(Entity target,LivingEntity user,float damage,int ticks,float spent){if(target.hasAttached(CHARGE))return;target.setAttached(CHARGE,new HamonCharge(damage,ticks,ticks,user==null?"":user.getUUID().toString(),spent,false,true,false,false));}
    public static void chargeBlock(LivingEntity user,BlockPos pos,float damage,int ticks,float spent){if(!(user.level() instanceof ServerLevel level) || !livingBlock(level.getBlockState(pos)))return;for(var entity:level.getEntitiesOfClass(HamonBlockChargeEntity.class,new AABB(pos)))entity.discard();level.addFreshEntity(new HamonBlockChargeEntity(user,pos,damage,ticks,spent));}
    static LivingEntity owner(Entity entity,HamonCharge charge){if(entity.level() instanceof ServerLevel level)try{Entity owner=level.getEntity(UUID.fromString(charge.owner()));return owner instanceof LivingEntity l?l:null;}catch(IllegalArgumentException ignored){}return null;}
    public static void tick(Entity entity){if(!(entity.level() instanceof ServerLevel level) || TimeStopService.isFrozen(entity))return;
        var charge=entity.getAttached(CHARGE);if(charge!=null){if(charge.remaining()<0)entity.removeAttached(CHARGE);else entity.setAttached(CHARGE,charge.projectile()?charge.tick():contact(entity,charge,null,entity.getBoundingBox().inflate(1)));}
        if(entity instanceof LivingEntity living){
            if(living.hasAttached(PRE_OWNER) && !living.hasEffect(HamonEffects.HYPNOSIS))relieveHypnosis(living);
            if(living instanceof ServerPlayer player && HamonService.hasHamon(player) && HamonService.state(player).hasSkill("rope_trap") && player.onGround() && player.isShiftKeyDown() && level.isEmptyBlock(player.blockPosition())){
                for(int n=8;n>=0;n--){ItemStack item=player.getInventory().getItem(n);if(item.is(Items.STRING)){level.setBlock(player.blockPosition(),Blocks.TRIPWIRE.defaultBlockState(),3);if(!player.isCreative())item.shrink(1);break;}}
            }
            tickImpact(living);
        }
    }
    public static HamonCharge contact(Entity charged,HamonCharge charge,BlockPos block,AABB box){
        if(!(charged.level() instanceof ServerLevel level))return charge;LivingEntity user=owner(charged,charge);String ownerId=charge.owner();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,box,e->e!=charged && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()) && !e.getUUID().toString().equals(ownerId))){
            float amount=charge.damage()*(charge.large()?1:.1F);
            if(VampireHamon.damageRaw(level,target,block==null?charged:null,charged instanceof LivingEntity?null:user,amount)){
                if(!target.isAlive() && user instanceof ServerPlayer player)HamonAdvancements.onChargeKill(player,target,block==null?charged:null,block);
                if(!charge.gavePoints() && user!=null)HamonService.gainActionPoints(user,true,charge.spent());
                if(charge.large()){
                    Vec3 center=block!=null?Vec3.atCenterOf(block):charged.getBoundingBox().getCenter();
                    if(user!=null)HamonActionService.knockback(target,user,.75F,target.position().subtract(center));
                    if(charged instanceof LivingEntity && user!=null && HamonService.state(user).hasSkill("hamon_shock") && HamonActionService.living(target) && !target.hasEffect(VampireCombatService.STUN))target.addEffect(new MobEffectInstance(HamonEffects.SHOCK,30,0,false,false));
                    VampireHamonParticles.emit(target,4);
                }
                charge=charge.hit(block!=null);
                if(block!=null && !level.getBlockState(block).is(Blocks.COBWEB))charge=charge.ticks(0);
            }
        }return charge.tick();
    }
    public static boolean livingBlock(BlockState state){String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();if(id.contains("dead"))return false;
        Block b=state.getBlock();return state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.is(BlockTags.FLOWERS)||state.is(BlockTags.SAPLINGS)||b instanceof BonemealableBlock || b instanceof CactusBlock || b instanceof SugarCaneBlock || b instanceof BambooStalkBlock || b instanceof BambooSaplingBlock || b instanceof TurtleEggBlock || b instanceof FlowerPotBlock && !state.is(Blocks.FLOWER_POT)||id.contains("coral")||id.contains("mossy")||id.contains("grass")||state.is(Blocks.PUMPKIN)||state.is(Blocks.MELON)||state.is(Blocks.HAY_BLOCK);}
    private static boolean livingItem(ItemStack s){if(s.getItem() instanceof BlockItem b)return livingBlock(b.getBlock().defaultBlockState());return s.is(Items.EGG)||s.is(Items.COD)||s.is(Items.SALMON)||s.is(Items.TROPICAL_FISH)||s.is(Items.PUFFERFISH)||s.getItem() instanceof MobBucketItem || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("gold_experience_body_tissue");}
    public static void chargeDrop(Player player,ItemEntity item){if(item.hasAttached(CHARGE))return;if(!HamonService.hasHamon(player) || !HamonService.state(player).hasSkill("plant_item_infusion") || HamonService.state(player).energy()<=0 || !livingItem(item.getItem()))return;var h=HamonService.state(player);float e=HamonService.consumeEnergy(player,200,"plant_item_infusion");charge(item,player,h.damageMultiplier()*e,100+(int)(1100*h.strengthLevel()/60F*e*e),200);}
    public static void chargeNew(Entity entity){
        if(entity instanceof Chicken && entity.level() instanceof ServerLevel level){Projectile egg=EGGS.get(level);if(egg!=null && egg.position().distanceToSqr(entity.position())<4){var c=egg.getAttached(CHARGE);if(c!=null){LivingEntity user=owner(egg,c);float mult=c.multiply() && user!=null?HamonService.state(user).damageMultiplier()*HamonService.bloodstreamEfficiency(user):1;charge(entity,user,c.projectileDamage()*mult,Integer.MAX_VALUE,0);}}}
        if(!(entity instanceof Projectile p) || p instanceof HamonProjectileEntity || !(p.getOwner() instanceof LivingEntity user))return;
        float damage,cost;int ticks;String skill;
        if(p instanceof AbstractArrow){damage=1.5F;cost=1000;ticks=10;skill="arrow_infusion";}
        else if(p instanceof Snowball){damage=.75F;cost=500;ticks=20;skill="throwables_infusion";}
        else if(p instanceof ThrownEgg){damage=.75F;cost=200;ticks=Integer.MAX_VALUE;skill="throwables_infusion";}
        else if(BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).equals(RipplesFabric.id("molotov"))){damage=1;cost=500;ticks=200;skill="throwables_infusion";}
        else if(p instanceof AbstractThrownPotion potion && water(potion)){damage=1;cost=750;ticks=30;skill="throwables_infusion";}
        else return;
        if(HamonService.hasHamon(user) && HamonService.state(user).hasSkill(skill) && HamonService.state(user).energy()>0){float spent=Math.min(cost,HamonService.state(user).energy());float e=HamonService.consumeEnergy(user,cost,skill);entity.setAttached(CHARGE,new HamonCharge(damage*e,ticks,ticks,user==null?"":user.getUUID().toString(),spent,false,true,true,true));}
        var inherited=user.getAttached(CHARGE);if(inherited!=null){user.setAttached(CHARGE,inherited.ticks(inherited.remaining()-(int)(inherited.initial()*cost/1000F)));entity.setAttached(CHARGE,new HamonCharge(damage*inherited.damage(),ticks,ticks,inherited.owner(),0,false,true,true,false));}
    }
    private static boolean water(AbstractThrownPotion potion){var contents=potion.getItem().get(DataComponents.POTION_CONTENTS);return contents!=null && contents.is(Potions.WATER);}
    public static void impact(Projectile p,HitResult hit){var c=p.getAttached(CHARGE);if(c==null || c.remaining()<0 || !(p.level() instanceof ServerLevel level))return;
        if(p instanceof AbstractThrownPotion potion && water(potion)){for(var target:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(4,2,4),e->e!=p.getOwner() && e.isAlive() && !e.isSpectator()))if(target.distanceToSqr(p.position())<16)projectileDamage(p,c,target);}
        else if(hit instanceof EntityHitResult h && h.getEntity() instanceof LivingEntity target)projectileDamage(p,c,target);
        if(p instanceof ThrownEgg)EGGS.put(level,p);
    }
    private static void projectileDamage(Projectile p,HamonCharge c,LivingEntity target){LivingEntity user=owner(p,c);boolean hit=c.multiply()?VampireHamon.damage((ServerLevel)p.level(),target,p,user,c.projectileDamage()):VampireHamon.damageRaw((ServerLevel)p.level(),target,p,user,c.projectileDamage());if(hit && c.multiply() && user!=null)HamonService.gainActionPoints(user,true,c.spent());}
    public static boolean ropeTrap(LivingEntity user,BlockPos pos){if(!HamonService.hasHamon(user) || !HamonService.state(user).hasSkill("rope_trap"))return false;float e=HamonService.efficiency(user,10,"rope_trap");var h=HamonService.state(user);rope(user,pos,64,40+(int)(160*h.strengthLevel()/60F*e),.02F*h.damageMultiplier()*e,new HashSet<>());return true;}
    private static void rope(LivingEntity user,BlockPos pos,int range,int duration,float damage,Set<BlockPos>visited){if(range<=0 || !visited.add(pos.immutable()) || !user.level().getBlockState(pos).is(Blocks.TRIPWIRE) || HamonService.state(user).energy()<=0)return;BlockState state=user.level().getBlockState(pos);if(HamonService.consumeEnergy(user,10,"rope_trap")<=0)return;HamonService.gainActionPoints(user,false,5);user.level().setBlock(pos,Blocks.COBWEB.defaultBlockState(),3);user.level().addFreshEntity(new HamonBlockChargeEntity(user,pos,damage,duration,5));
        if(state.getValue(TripWireBlock.EAST))rope(user,pos.east(),range-1,duration,damage,visited);if(state.getValue(TripWireBlock.WEST))rope(user,pos.west(),range-1,duration,damage,visited);if(state.getValue(TripWireBlock.NORTH))rope(user,pos.north(),range-1,duration,damage,visited);if(state.getValue(TripWireBlock.SOUTH))rope(user,pos.south(),range-1,duration,damage,visited);}
    public static boolean preventBlockDamage(LivingEntity entity,DamageSource source,float amount){
        if(!(entity.level() instanceof ServerLevel level) || (!source.is(DamageTypes.CACTUS) && !source.is(DamageTypes.SWEET_BERRY_BUSH)))return false;
        AABB box=entity.getBoundingBox();
        BlockPos min=BlockPos.containing(box.minX+.001,box.minY+.001,box.minZ+.001);
        BlockPos max=BlockPos.containing(box.maxX-.001,box.maxY-.001,box.maxZ-.001);
        boolean found=false;
        if(level.hasChunksAt(min,max)){
            for(int x=min.getX();x<=max.getX();x++)for(int y=min.getY();y<=max.getY();y++)for(int z=min.getZ();z<=max.getZ();z++){
                BlockPos pos=new BlockPos(x,y,z);BlockState block=level.getBlockState(pos);
                if(source.is(DamageTypes.CACTUS)?block.getBlock() instanceof CactusBlock:block.getBlock() instanceof SweetBerryBushBlock){
                    found=true;
                    if(!protectFromBlock(entity,source,amount,pos,block))return false;
                }
            }
        }
        return found || protectFromBlock(entity,source,amount,null,null);
    }
    /** Original Hamon contact protection also guards against Pillar Man absorption. */
    public static boolean protectFromContact(LivingEntity entity,DamageSource source,float amount){
        return entity.level() instanceof ServerLevel && protectFromBlock(entity,source,amount,null,null);
    }
    private static boolean protectFromBlock(LivingEntity entity,DamageSource source,float amount,BlockPos pos,BlockState block){
        boolean prevented=false;
        if(HamonService.hasHamon(entity)){
            if(entity instanceof HamonMasterEntity)prevented=true;
            else{
                float energy=HamonService.state(entity).energy(),cost=amount*.5F;
                HamonService.consumeResourceEnergy(entity,Math.min(cost,energy));
                prevented=energy>=cost;
            }
        }
        if(!prevented){
            var charge=entity.getAttached(CHARGE);
            if(charge!=null && charge.remaining()>=0){
                entity.setAttached(CHARGE,charge.ticks(charge.remaining()-Math.max((int)amount,1)));
                prevented=true;
            }
        }
        if(prevented){
            Vec3 spark=null;
            if(pos!=null && block!=null){
                var shape=block.getCollisionShape(entity.level(),pos);
                if(!shape.isEmpty()){
                    AABB intersection=entity.getBoundingBox().intersect(shape.bounds().move(pos));
                    var random=entity.getRandom();
                    spark=new Vec3(net.minecraft.util.Mth.lerp(random.nextDouble(),intersection.minX,intersection.maxX),
                            net.minecraft.util.Mth.lerp(random.nextDouble(),intersection.minY,intersection.maxY),
                            net.minecraft.util.Mth.lerp(random.nextDouble(),intersection.minZ,intersection.maxZ));
                }
            }
            if(spark==null)spark=source.getSourcePosition();
            if(spark!=null)VampireHamonParticles.shortSpark(entity,spark,Math.max((int)(amount*.5F),1),Math.min(amount*.25F,1));
        }
        return prevented;
    }
    public static boolean snakeMuffler(LivingEntity entity,DamageSource source,float amount){if(!(entity instanceof ServerPlayer player) || !player.onGround() || !player.getItemBySlot(EquipmentSlot.HEAD).is(VampireHamon.SCARF) || source.getDirectEntity()!=source.getEntity() || !(source.getEntity() instanceof LivingEntity attacker) || !HamonService.state(player).hasSkill("snake_muffler") || HamonService.state(player).energy()<500)return false;
        var scarf=player.getItemBySlot(EquipmentSlot.HEAD);if(player.getCooldowns().isOnCooldown(scarf))return false;player.getCooldowns().addCooldown(scarf,80);float e=HamonService.efficiency(player,500,"snake_muffler");if(e!=1 && e<amount/player.getMaxHealth())return false;HamonService.consumeEnergy(player,500,"snake_muffler");HamonActionService.damage(attacker,player,.75F);attacker.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,200));var snake=new HamonProjectileEntity(HamonEntities.SNAKE,player).snake(attacker);snake.setPos(Vec3.atBottomCenterOf(player.blockPosition()));player.level().addFreshEntity(snake);return true;}
    public static boolean canHypnotize(LivingEntity target,LivingEntity user){if(target instanceof TamableAnimal tame)return tame.getOwnerReference()==null || !tame.getOwnerReference().getUUID().equals(user.getUUID());if(target instanceof AbstractHorse horse)return horse.getOwnerReference()==null || !horse.getOwnerReference().getUUID().equals(user.getUUID());return false;}
    public static void hypnotize(LivingEntity target,ServerPlayer user,int duration){if(duration<=0 || !canHypnotize(target,user))return;
        EntityReference<LivingEntity>old=target instanceof TamableAnimal tame?tame.getOwnerReference():((AbstractHorse)target).getOwnerReference();target.setAttached(PRE_OWNER,old==null?"":old.getUUID().toString());
        if(target instanceof TamableAnimal tame)tame.tame(user);else if(target instanceof AbstractHorse horse){horse.setOwner(user);horse.setTamed(true);}target.addEffect(new MobEffectInstance(HamonEffects.HYPNOSIS,duration,0,false,false,true));target.level().broadcastEntityEvent(target,(byte)7);}
    public static void relieveHypnosis(LivingEntity target){String prev=target.getAttached(PRE_OWNER);if(prev==null)return;EntityReference<LivingEntity>owner=prev.isEmpty()?null:EntityReference.of(UUID.fromString(prev));if(target instanceof TamableAnimal tame){tame.setOrderedToSit(false);tame.setTame(owner!=null,true);tame.setOwnerReference(owner);}else if(target instanceof AbstractHorse horse){horse.setTamed(owner!=null);((com.github.standobyte.jojo.fabric.mixin.HamonHorseAccess)horse).jojo$setHamonOwner(owner);}target.removeAttached(PRE_OWNER);target.level().broadcastEntityEvent(target,(byte)6);}
    public static void collision(LivingEntity victim,ServerPlayer user,float damage,int fire){VampireCollisionImpact.start(victim,user);if(damage>0)IMPACTS.put(victim,new Impact(user,damage,fire,new HashSet<>(),victim.getDeltaMovement().normalize()));}
    private static void tickImpact(LivingEntity victim){var impact=IMPACTS.get(victim);if(impact==null)return;Vec3 motion=victim.getDeltaMovement();if(!victim.isAlive() || motion.lengthSqr()<1E-7 || motion.normalize().dot(impact.direction())<=0){IMPACTS.remove(victim);return;}
        for(var target:victim.level().getEntitiesOfClass(LivingEntity.class,victim.getBoundingBox().expandTowards(motion).inflate(.25),e->e!=victim && e!=impact.owner() && e.isAlive()))if(impact.hit().add(target.getUUID()) && VampireHamon.damage((ServerLevel)victim.level(),target,victim,impact.owner(),impact.damage()*.5F)){if(impact.fire()>0)target.igniteForTicks(impact.fire());}}
    public static void explode(Entity exploder,float radius){
        var charge=exploder.getAttached(CHARGE);if(charge==null || !(exploder.level() instanceof ServerLevel level))return;
        LivingEntity user=owner(exploder,charge);Vec3 center=exploder.position();float diameter=radius*2;
        var blast=new net.minecraft.world.level.ServerExplosion(level,exploder,null,null,center,radius,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(diameter+1),e->e!=exploder && e.isAlive() && !e.isSpectator())){
            double fraction=target.position().distanceTo(center)/diameter;if(fraction>1 || target.ignoreExplosion(blast))continue;
            double exposure=net.minecraft.world.level.ServerExplosion.getSeenPercent(center,target),impact=(1-fraction)*exposure;
            float damage=(float)((impact*impact+impact)/2*7*diameter+1)*charge.damage();VampireHamon.damageRaw(level,target,exploder,user,damage);
        }
    }
    private HamonCharges(){}
}
