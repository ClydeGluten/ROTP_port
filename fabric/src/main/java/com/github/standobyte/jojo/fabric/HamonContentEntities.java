package com.github.standobyte.jojo.fabric;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
public final class HamonContentEntities {
    private static <T extends Entity> EntityType<T> register(String id,EntityType.Builder<T> builder){
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id(id),builder.build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id(id))));
    }
    public static final EntityType<HamonMasterEntity> HAMON_MASTER=register("hamon_master",EntityType.Builder.of(HamonMasterEntity::new,MobCategory.CREATURE).sized(.6F,1.95F));
    public static final EntityType<LeavesGliderEntity> LEAVES_GLIDER=register("leaves_glider",EntityType.Builder.<LeavesGliderEntity>of(LeavesGliderEntity::new,MobCategory.MISC).sized(2.5F,.125F).updateInterval(1));
    public static final EntityType<ClackersEntity> CLACKERS=register("clackers",EntityType.Builder.<ClackersEntity>of(ClackersEntity::new,MobCategory.MISC).sized(.5F,.5F).updateInterval(20));
    public static final EntityType<CrimsonBubbleEntity> CRIMSON_BUBBLE=register("crimson_bubble",EntityType.Builder.<CrimsonBubbleEntity>of(CrimsonBubbleEntity::new,MobCategory.MISC).sized(.625F,.625F).noSummon().updateInterval(20));
    public static final EntityType<MolotovEntity> MOLOTOV=register("molotov",EntityType.Builder.<MolotovEntity>of(MolotovEntity::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(4).updateInterval(10));
    public static final Item SPAWN_EGG;
    static{var key=ResourceKey.create(Registries.ITEM,RipplesFabric.id("hamon_master_spawn_egg"));SPAWN_EGG=Registry.register(BuiltInRegistries.ITEM,key,new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(HAMON_MASTER)));}
    public static void initialize(){FabricDefaultAttributeRegistry.register(HAMON_MASTER,HamonMasterEntity.createAttributes());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(o->o.accept(SPAWN_EGG));}
    private HamonContentEntities(){}
}
