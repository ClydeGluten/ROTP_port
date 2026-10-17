package com.github.standobyte.jojo.fabric.stand.magiciansred;

import com.github.standobyte.jojo.fabric.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.BaseFireBlock;
import static com.github.standobyte.jojo.fabric.StandActionRegistry.Hotbar.*;

/** Ten original actions and six distinct source projectile types. Task entity owns perform-time costs. */
public final class MagiciansRedActions {
    public static final String ID="jojo:magicians_red";
    public static final Identifier PUNCH=id("punch"),HEAVY=id("heavy_punch"),KICK=id("kick"),FLAME_BURST=id("flame_burst"),
            FIREBALL=id("fireball"),CROSSFIRE=id("crossfire_hurricane"),SPECIAL=id("ch_special"),BLOCK=id("block"),BIND=id("red_bind"),DETECTOR=id("detector");
    public static final EntityType<MagiciansRedEntity> STAND=entity("magicians_red",EntityType.Builder.<MagiciansRedEntity>of(MagiciansRedEntity::new,MobCategory.MISC).sized(.65F,1.95F).clientTrackingRange(10).updateInterval(1).noSave().noSummon());
    public static final EntityType<MRFlameEntity> FLAME=entity("mr_flame",EntityType.Builder.<MRFlameEntity>of(MRFlameEntity::new,MobCategory.MISC).sized(.0625F,.0625F).updateInterval(10).noSave().noSummon());
    public static final EntityType<MRFireballEntity> FIREBALL_ENTITY=entity("mr_fireball",EntityType.Builder.<MRFireballEntity>of(MRFireballEntity::new,MobCategory.MISC).sized(.25F,.25F).updateInterval(10).noSave().noSummon());
    public static final EntityType<MRCrossfireEntity> CROSSFIRE_ENTITY=entity("mr_crossfire_hurricane",EntityType.Builder.<MRCrossfireEntity>of(MRCrossfireEntity::new,MobCategory.MISC).sized(1.25F,1.8F).updateInterval(10).noSave().noSummon());
    public static final EntityType<MRCrossfireSpecialEntity> CROSSFIRE_SPECIAL_ENTITY=entity("mr_crossfire_hurricane_special",EntityType.Builder.<MRCrossfireSpecialEntity>of(MRCrossfireSpecialEntity::new,MobCategory.MISC).sized(.625F,.9F).updateInterval(10).noSave().noSummon());
    public static final EntityType<MRRedBindEntity> BIND_ENTITY=entity("mr_red_bind",EntityType.Builder.<MRRedBindEntity>of(MRRedBindEntity::new,MobCategory.MISC).sized(.25F,.25F).updateInterval(20).noSave().noSummon());
    public static final EntityType<MRDetectorEntity> DETECTOR_ENTITY=entity("mr_detector",EntityType.Builder.<MRDetectorEntity>of(MRDetectorEntity::new,MobCategory.MISC).sized(.5F,.5F).updateInterval(Integer.MAX_VALUE).dontTrackDeltas().noSave().noSummon());
    private static boolean initialized;
    private static Identifier id(String path){return RipplesFabric.id("magicians_red_"+path);}
    private static <T extends Entity> EntityType<T> entity(String path,EntityType.Builder<T> builder){var key=ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id(path));return Registry.register(BuiltInRegistries.ENTITY_TYPE,key,builder.build(key));}
    private static StandActionRegistry.Builder base(Identifier id){return StandActionRegistry.Definition.builder(id,ID);}
    public static void initialize(){
        if(initialized)return;initialized=true;
        var arms=StandParts.Part.ARMS;var body=StandParts.Part.MAIN_BODY;
        StandActionRegistry.registerReplacement(ID,MagiciansRedActions::replace);
        register(base(PUNCH).hotbar(LEFT,0).parts(arms).shift(HEAVY).cost(10,0).recoveryFollowupOf(PUNCH,KICK).build(),MagiciansRedEntity.Task.LIGHT);
        register(base(HEAVY).parts(arms).cost(50,0).recoveryFollowupOf(PUNCH,KICK).build(),MagiciansRedEntity.Task.HEAVY);
        register(base(KICK).parts(StandParts.Part.LEGS).cost(50,0).training(1,false,1).recoveryFollowupOf(PUNCH,KICK).build(),MagiciansRedEntity.Task.KICK);
        register(base(FLAME_BURST).hotbar(LEFT,1).parts(body).hold(Integer.MAX_VALUE,0,false).cost(0,3).movement(.5F).build(),MagiciansRedEntity.Task.FLAME_BURST);
        register(base(FIREBALL).hotbar(LEFT,2).parts(body).cost(75,0).training(2,false,1).build(),MagiciansRedEntity.Task.FIREBALL);
        register(base(CROSSFIRE).hotbar(LEFT,3).parts(body).shift(SPECIAL).hold(20,20,false).cost(500,0).training(4,true,1).build(),MagiciansRedEntity.Task.CROSSFIRE);
        register(base(SPECIAL).parts(body).hold(20,20,false).cost(500,0).training(-1,false,1).build(),MagiciansRedEntity.Task.CROSSFIRE_SPECIAL);
        register(base(BLOCK).hotbar(RIGHT,0).parts(arms).hold(Integer.MAX_VALUE,0,false).movement(.3F).build(),MagiciansRedEntity.Task.BLOCK);
        register(base(BIND).hotbar(RIGHT,1).parts(arms).hold(Integer.MAX_VALUE,0,false).cost(0,1).movement(.3F).training(1,false,1).build(),MagiciansRedEntity.Task.RED_BIND);
        StandActionRegistry.register(base(DETECTOR).hotbar(RIGHT,2).parts(body).training(3,false,1).build(),new StandActionRegistry.Handler(){
            @Override public boolean autoSummons(){return true;}
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context c){return true;}
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context c){var prepared=StandService.prepareSummon(c.player());if(prepared.manifestation() instanceof MagiciansRedEntity stand)stand.prepareActionSummon(MagiciansRedEntity.Task.NONE,c.manifestation()==null);return prepared;}
            @Override public boolean canRunAlongsideHeld(StandActionRegistry.Context c,Identifier active){return active.equals(FLAME_BURST)||active.equals(BLOCK)||active.equals(BIND);}
            @Override public boolean execute(StandActionRegistry.Context c){return toggleDetector(c.player());}
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{for(var player:server.getPlayerList().getPlayers())if(player.getAttachedOrCreate(StandAttachments.PROGRESS).standId().equals(ID))unlockSpecial(player);});
    }
    public static Identifier replace(ServerPlayer player,Identifier action){
        if(!action.equals(HEAVY)||!StandParts.has(player,StandParts.Part.LEGS))return action;
        if(!StandParts.has(player,StandParts.Part.ARMS))return KICK;
        return StandService.currentManifestation(player) instanceof MagiciansRedEntity stand&&(stand.task()==MagiciansRedEntity.Task.KICK||stand.finisherCharge()>=.5F)&&StandActionTraining.unlocked(player,KICK)?KICK:action;
    }
    private static void register(StandActionRegistry.Definition definition,MagiciansRedEntity.Task task){
        StandActionRegistry.register(definition,new StandActionRegistry.Handler(){
            @Override public boolean ownsTaskLifecycle(){return true;}
            @Override public boolean autoSummons(){return true;}
            @Override public boolean canUseWithoutManifestation(StandActionRegistry.Context c){return !VampireCombatService.isStunned(c.player());}
            @Override public StandActionRegistry.Preparation prepare(StandActionRegistry.Context c){var prepared=StandService.prepareSummon(c.player());if(prepared.manifestation() instanceof MagiciansRedEntity stand)stand.prepareActionSummon(task,c.manifestation()==null);return prepared;}
            @Override public boolean canReplaceHeld(StandActionRegistry.Context c,Identifier active){return active.equals(FLAME_BURST)||active.equals(BLOCK)||active.equals(BIND);}
            @Override public boolean canUse(StandActionRegistry.Context c){return c.manifestation() instanceof MagiciansRedEntity stand&&stand.canUse(task);}
            @Override public boolean execute(StandActionRegistry.Context c){return ((MagiciansRedEntity)c.manifestation()).start(task);}
            @Override public void keep(StandActionRegistry.Context c){c.manifestation().refreshHeld();}
            @Override public boolean tick(StandActionRegistry.Context c,int ticks){var stand=(MagiciansRedEntity)c.manifestation();stand.refreshHeld();return stand.task()==task;}
            @Override public void release(StandActionRegistry.Context c,int ticks,boolean cancelled){((MagiciansRedEntity)c.manifestation()).releaseHeld(cancelled,ticks);}
        });
    }
    public static void trainCrossfire(ServerPlayer player,float amount){StandActionTraining.add(player,CROSSFIRE,amount,1,4);unlockSpecial(player);}
    private static void unlockSpecial(ServerPlayer player){if(StandActionTraining.rawPoints(player,ID,CROSSFIRE)>=1)StandActionTraining.unlock(player,SPECIAL,false,1);}
    public static boolean toggleDetector(ServerPlayer player){
        var detectors=player.level().getEntitiesOfClass(MRDetectorEntity.class,player.getBoundingBox().inflate(5),entity->entity.owner()==player);
        if(detectors.isEmpty())player.level().addFreshEntity(new MRDetectorEntity(player));else detectors.forEach(Entity::discard);return true;
    }
    public static boolean isBlocking(LivingEntity target){return !target.getUseItem().isEmpty()&&target.getUseItem().getItem() instanceof ShieldItem||target instanceof StandManifestation stand&&stand.actionPhase()==5;}
    /** Original player START tick hook: clear any vanilla/custom fire intersecting the living MR user's body. */
    public static void removeFireUnderPlayer(ServerPlayer player){
        if(!player.isAlive()||!(StandService.currentManifestation(player) instanceof MagiciansRedEntity))return;
        var box=player.getBoundingBox();var min=BlockPos.containing(box.minX+.001,box.minY+.001,box.minZ+.001);var max=BlockPos.containing(box.maxX-.001,box.maxY-.001,box.maxZ-.001);
        if(!player.level().hasChunksAt(min,max))return;
        for(var mutable:BlockPos.betweenClosed(min,max)){var pos=mutable.immutable();if(player.level().getBlockState(pos).getBlock() instanceof BaseFireBlock)player.level().destroyBlock(pos,false);}
    }
    private MagiciansRedActions(){}
}
