package com.github.standobyte.jojo.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import java.util.HashMap;
import java.util.UUID;

/** Vampire-facing Hamon rules. Hamon learning/acquisition is a separate power subsystem. */
public final class VampireHamon {
    public record Memory(boolean formerUser, float strength, String technique) {
        public static final Memory EMPTY = new Memory(false,0,"");
        public Memory { if (!Float.isFinite(strength) || strength<0 || strength>60 || technique==null || technique.length()>256) throw new IllegalArgumentException("Invalid Hamon memory"); }
    }
    private static final Codec<Memory> CODEC = RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("former_user",false).forGetter(Memory::formerUser),
            Codec.floatRange(0,60).optionalFieldOf("strength",0F).forGetter(Memory::strength),
            Codec.STRING.optionalFieldOf("technique","").forGetter(Memory::technique)).apply(i,Memory::new));
    public static final AttachmentType<Memory> MEMORY=AttachmentRegistry.<Memory>builder().initializer(()->Memory.EMPTY).persistent(CODEC).copyOnDeath()
            .syncWith(StreamCodec.<RegistryFriendlyByteBuf,Memory>of((b,v)->{b.writeBoolean(v.formerUser());b.writeFloat(v.strength());b.writeUtf(v.technique(),256);},
                    b->new Memory(b.readBoolean(),b.readFloat(),b.readUtf(256))),AttachmentSyncPredicate.targetOnly()).buildAndRegister(RipplesFabric.id("vampire_hamon_memory"));
    public static final AttachmentType<Integer> CHARGE=AttachmentRegistry.<Integer>builder().initializer(()->0)
            .syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("vampire_hamon_charge"));
    public static final Holder<MobEffect> SPREAD=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,RipplesFabric.id("hamon_spread"),
            new MobEffect(MobEffectCategory.HARMFUL,0xF9EF9C) {
                @Override public boolean shouldApplyEffectTickThisTick(int duration,int amplifier){return true;}
                @Override public boolean applyEffectTick(ServerLevel level,LivingEntity entity,int amplifier){
                    if (entity instanceof net.minecraft.world.entity.monster.Ghast || entity instanceof net.minecraft.world.entity.monster.Phantom) {
                        var v=entity.getDeltaMovement(); entity.setDeltaMovement(v.x,-entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)*(amplifier+1)*.5,v.z);
                    }
                    return true;
                }
            });
    public static final Item SCARF=Registry.register(BuiltInRegistries.ITEM,RipplesFabric.id("satiporoja_scarf"),new Item(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM,RipplesFabric.id("satiporoja_scarf"))).stacksTo(1).equippable(EquipmentSlot.HEAD).rarity(Rarity.UNCOMMON).enchantable(25)
            .attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(RipplesFabric.id("satiporoja_scarf_armor"),1,
                                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD).build())) {
                @Override public boolean isFoil(ItemStack stack){return true;}
            });
    private static final TagKey<EntityType<?>> AFFECTED=TagKey.create(Registries.ENTITY_TYPE,RipplesFabric.id("hamon_damage"));
    private static final HashMap<UUID,Integer> LEASES=new HashMap<>();
    /** Called by the Hamon power adapter before replacing its state with vampirism. */
    public static void recordHamonTraining(Player player,float strength,String technique){player.setAttached(MEMORY,new Memory(false,strength,technique));}
    public static void rememberOnTransformation(Player player){
        if(HamonService.hasHamon(player)){
            var hamon=HamonService.state(player);
            player.setAttached(MEMORY,new Memory(true,hamon.strengthLevel(),hamon.technique()));
            HamonService.clear(player);
        }else{
            var old=player.getAttachedOrCreate(MEMORY);
            if(old.strength()>0||!old.technique().isEmpty())player.setAttached(MEMORY,new Memory(true,old.strength(),old.technique()));
        }
    }
    public static boolean affected(LivingEntity entity){return SpeciesHooks.undeadOrVampiric(entity) || entity.typeHolder().is(AFFECTED);}
    public static float reduceHealing(LivingEntity entity,float amount){
        var effect=entity.getEffect(SPREAD); return effect==null?amount:amount*(1-Math.min(effect.getAmplifier()+1,5)*.2F);
    }
    private static final AttachmentType<Float> RECEIVED_DAMAGE=AttachmentRegistry.<Float>builder().initializer(()->0F).buildAndRegister(RipplesFabric.id("hamon_received_damage"));
    public static void tickHamonSpread(LivingEntity entity){float old=entity.getAttachedOrCreate(RECEIVED_DAMAGE);if(old>0)entity.setAttached(RECEIVED_DAMAGE,Math.max(0,old-.1F));}
    public static boolean damage(ServerLevel level,LivingEntity victim,LivingEntity source,float amount){return damage(level,victim,source,null,amount,true);}
    public static boolean damageRaw(ServerLevel level,LivingEntity victim,LivingEntity source,float amount){return damage(level,victim,source,null,amount,false);}
    public static boolean damage(ServerLevel level,LivingEntity victim,Entity direct,Entity indirect,float amount){return damage(level,victim,direct,indirect,amount,true);}
    public static boolean damageRaw(ServerLevel level,LivingEntity victim,Entity direct,Entity indirect,float amount){return damage(level,victim,direct,indirect,amount,false);}
    private static boolean damage(ServerLevel level,LivingEntity victim,Entity direct,Entity indirect,float amount,boolean userMultiplier){
        if(!(amount>0)||!Float.isFinite(amount)||HamonCharges.isCharged(victim)||SpeciesHooks.stoneForm(victim))return false;
        boolean scarf=victim.getItemBySlot(EquipmentSlot.HEAD).is(SCARF);
        if(scarf&&HamonService.hasHamon(victim))return false;
        float base=amount*(affected(victim)?(PillarmanService.hasPillarman(victim)?.5F:1):.2F)*(scarf?.25F:1);
        Entity source=indirect==null?direct:indirect;
        LivingEntity hamonUser=userMultiplier&&source instanceof LivingEntity living&&HamonService.hasHamon(living)?living:null;
        float multiplied=base*(hamonUser==null?1:HamonService.state(hamonUser).damageMultiplier());
        float adjusted=multiplied*HamonConfig.server().hamonDamageMultiplier();
        var type=level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,RipplesFabric.id("hamon")));
        boolean hit=StandDamage.hurt(level,victim,new net.minecraft.world.damagesource.DamageSource(type,direct,source),adjusted);
        if(hit){
            if(affected(victim)&&scarf&&victim instanceof ServerPlayer player)VampireAdvancements.onScarfHamonDamage(player);
            VampireHamonParticles.emit(victim,adjusted/35F);
            if(hamonUser!=null&&affected(victim)&&!scarf&&HamonService.state(hamonUser).hasSkill("hamon_spread")){
                float received=victim.getAttachedOrCreate(RECEIVED_DAMAGE)+multiplied;victim.setAttached(RECEIVED_DAMAGE,received);
                float[] thresholds={5,12.5F,25,50,125};
                for(int i=4;i>=0;i--)if(received>=thresholds[i]){
                    int duration=60+40*i;victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration,i,false,false,true));
                    victim.addEffect(new MobEffectInstance(SPREAD,duration,i,false,false,false));break;
                }
            }
        }
        return hit;
    }
    public static boolean start(ServerPlayer player){
        if(!VampireActionPayload.canStart(player, VampireActionPayload.Action.START_HAMON) || !player.isAlive() || player.isSpectator() || !VampireService.progress(player).vampire() || !player.getAttachedOrCreate(MEMORY).formerUser()
                || TimeStopService.isFrozen(player) || player.getAttachedOrCreate(CHARGE)>0)return false;
        LEASES.put(player.getUUID(),0);player.setAttached(CHARGE,1);
        String technique=player.getAttachedOrCreate(MEMORY).technique();
        if(technique.startsWith("jojo:"))technique=technique.substring(5);
        switch(technique){
            case "jonathan", "zeppeli", "joseph", "caesar", "lisa_lisa" -> player.level().playSound(null,
                    player.getX(),player.getY(),player.getZ(),JojoSounds.get(technique+"_breath"),player.getSoundSource(),1,1);
            default -> { }
        }
        return true;
    }
    public static void keep(ServerPlayer player){if(LEASES.containsKey(player.getUUID()))LEASES.put(player.getUUID(),0);}
    public static void stop(ServerPlayer player){LEASES.remove(player.getUUID());player.setAttached(CHARGE,0);}
    public static void initialize(){
        VampireHamonParticles.initialize();
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(o->o.accept(SCARF));
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers()){
            int ticks=player.getAttachedOrCreate(CHARGE);if(ticks==0)continue;
            if(!player.isAlive() || !VampireService.progress(player).vampire() || LEASES.getOrDefault(player.getUUID(),16)>15){stop(player);continue;}
            if(TimeStopService.isFrozen(player))continue;
            LEASES.computeIfPresent(player.getUUID(),(id,n)->n+1);
            if(ticks%10==5)damage(player.level(),player,player,4);
            if(ticks==30)player.addEffect(new MobEffectInstance(SPREAD,100,1));
            if(ticks>=100){
                float strength=player.getAttachedOrCreate(MEMORY).strength();
                var center=player.getBoundingBox().getCenter();damage(player.level(),player,player,200);
                var explosion = new net.minecraft.world.level.ServerExplosion(player.level(),player,null,null,center,6,false,
                        net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
                if(strength>0)for(var target:player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(center,center).inflate(13),e->e!=player && e.isAlive() && !e.isSpectator())){
                    double fraction=target.position().distanceTo(center)/12.0;
                    if(fraction>1 || target.ignoreExplosion(explosion) || target.getEyePosition().distanceToSqr(center)<=1.0E-8)continue;
                    double exposure=net.minecraft.world.level.ServerExplosion.getSeenPercent(center,target);
                    double impact=(1-fraction)*exposure;
                    float blastDamage=(float)((impact*impact+impact)/2*7*12+1);
                    damage(player.level(),target,player,blastDamage*(strength*.1F));
                }
                stop(player);
            }else player.setAttached(CHARGE,ticks+1);
        }});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->LEASES.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->server.execute(()->stop(handler.player)));
    }
    private VampireHamon() { }
}
