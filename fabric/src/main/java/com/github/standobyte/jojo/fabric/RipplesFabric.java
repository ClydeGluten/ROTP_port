package com.github.standobyte.jojo.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RipplesFabric implements ModInitializer {
    public static final String MOD_ID = "jojo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final EntityType<StarPlatinumEntity> STAR_PLATINUM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("star_platinum"),
            EntityType.Builder.of(StarPlatinumEntity::new, MobCategory.MISC).sized(0.7F, 2.1F)
                    .eyeHeight(1.9F).clientTrackingRange(10).updateInterval(1).noSave().noSummon().fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("star_platinum"))));

    public static final EntityType<StarPlatinumEntity> THE_WORLD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("the_world"),
            EntityType.Builder.of(StarPlatinumEntity::new, MobCategory.MISC).sized(0.7F, 2.1F)
                    .eyeHeight(1.9F).clientTrackingRange(10).updateInterval(1).noSave().noSummon().fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("the_world"))));

    public static final EntityType<KnifeEntity> KNIFE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("knife"),
            EntityType.Builder.<KnifeEntity>of(KnifeEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("knife"))));

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STAR_PLATINUM, Mob.createMobAttributes());
        FabricDefaultAttributeRegistry.register(THE_WORLD, Mob.createMobAttributes());
        StandConfig.initialize();
        ResolveExtras.initialize();
        StandAttachments.initialize();
        JojoGameRules.initialize();
        StandItems.initialize();
        com.github.standobyte.jojo.fabric.equipment.EquipmentContent.initialize();
        VampireConfig.initialize();
        VampireAttachments.initialize();
        VampireBlocks.initialize();
        VampireItems.initialize();
        StoneMaskLoot.initialize(VampireItems.STONE_MASK);
        StandArrowLoot.initialize(StandItems.STAND_ARROW_BEETLE);
        StandArrowService.initialize();
        JojoSounds.initialize();
        VampireAdvancements.initialize();
        SledgehammerItem.initialize();
        VampireService.initialize();
        SpeciesHooks.initialize();
        com.github.standobyte.jojo.fabric.pillarman.PillarWorldContent.initialize();
        VampireBloodParticles.initialize();
        VampireBlood.initialize();
        VampireHamon.initialize();
        HamonService.initialize();
        HamonCommands.initialize();
        HamonItems.initialize();
        HamonInheritance.initialize();
        HamonContentEntities.initialize();
        HamonTempleStructure.initialize();
        HamonTempleTrades.initialize();
        HamonAdvancements.initialize();
        HamonParticles.initialize();
        HamonActionService.initialize();
        VampireCombatService.initialize();
        VampireZombieService.initialize();
        VampirePresentation.initialize();
        TrackedEffects.initialize();
        StandPerception.initialize();
        KnifeService.initialize();
        StuckKnives.initialize();
        StandService.initialize();
        com.github.standobyte.jojo.fabric.stand.StandHeavyImpact.initialize();
        com.github.standobyte.jojo.fabric.stand.tracking.TrackingIntegration.initialize();
        com.github.standobyte.jojo.fabric.stand.restoration.BlockHistorySavedData.initialize();
        com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedFireBlock.initialize();
        com.github.standobyte.jojo.fabric.stand.StandAfterimageEntity.initialize();
        com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotActions.initialize();
        FabricDefaultAttributeRegistry.register(com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotActions.STAND,
                com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity.createAttributes());
        StandService.registerFactory("jojo:silver_chariot",level->new com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotEntity(
                com.github.standobyte.jojo.fabric.stand.silverchariot.SilverChariotActions.STAND,level));
        com.github.standobyte.jojo.fabric.stand.hierophant.HierophantGreenActions.initialize();
        com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedActions.initialize();
        FabricDefaultAttributeRegistry.register(com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedActions.STAND,
                com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedEntity.createAttributes());
        StandService.registerFactory("jojo:magicians_red",level->new com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedEntity(
                com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedActions.STAND,level));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server->{
            for(var player:server.getPlayerList().getPlayers())com.github.standobyte.jojo.fabric.stand.magiciansred.MagiciansRedActions.removeFireUnderPlayer(player);
        });
        ProgressionService.initialize();
        TimeStopService.initialize();
        StandTimeSkipPayload.initialize();
        StandActionPayload.initialize();
        StandControlPayload.initialize();
        StandCommands.initialize();
        StandHudState.initialize();
        LOGGER.info("Ripples of the Past Fabric development port loaded; feature parity is incomplete.");
    }
}
