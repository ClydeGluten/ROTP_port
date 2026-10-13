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

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STAR_PLATINUM, Mob.createMobAttributes());
        StandAttachments.initialize();
        StandItems.initialize();
        StandArrowLoot.initialize(StandItems.STAND_ARROW_BEETLE);
        StandArrowService.initialize();
        JojoSounds.initialize();
        StandService.initialize();
        ProgressionService.initialize();
        TimeStopService.initialize();
        StandActionPayload.initialize();
        StandControlPayload.initialize();
        StandCommands.initialize();
        LOGGER.info("Ripples of the Past Fabric development port loaded; feature parity is incomplete.");
    }
}
