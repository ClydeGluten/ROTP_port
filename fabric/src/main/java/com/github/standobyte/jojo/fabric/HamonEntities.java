package com.github.standobyte.jojo.fabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
public final class HamonEntities {
    private static EntityType<HamonProjectileEntity> projectile(String id,float width,float height,int interval,boolean noSummon,boolean immune){
        var builder=EntityType.Builder.<HamonProjectileEntity>of(HamonProjectileEntity::new,MobCategory.MISC).sized(width,height).clientTrackingRange(8).updateInterval(interval);
        if(noSummon)builder.noSummon();if(immune)builder.fireImmune();
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id(id),builder.build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id(id))));}
    public static final EntityType<HamonProjectileEntity> BUBBLE=projectile("hamon_bubble",.15F,.15F,10,false,false);
    public static final EntityType<HamonProjectileEntity> CUTTER=projectile("hamon_cutter",.5F,.125F,10,false,false);
    public static final EntityType<HamonProjectileEntity> BUBBLE_CUTTER=projectile("hamon_bubble_cutter",.325F,.0875F,10,false,false);
    public static final EntityType<HamonProjectileEntity> BARRIER=projectile("hamon_bubble_barrier",2,2,3,false,false);
    public static final EntityType<HamonProjectileEntity> TURQUOISE=projectile("turquoise_blue_overdrive",4,4,20,true,true);
    public static final EntityType<HamonProjectileEntity> ZOOM=projectile("zoom_punch",.25F,.25F,20,true,false);
    public static final EntityType<HamonProjectileEntity> SNAKE=projectile("snake_muffler",.5F,.5F,3,true,false);
    public static final EntityType<HamonSendoEntity> SENDO=Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id("sendo_hamon_overdrive"),
            EntityType.Builder.<HamonSendoEntity>of(HamonSendoEntity::new,MobCategory.MISC).sized(4,4).clientTrackingRange(8).updateInterval(Integer.MAX_VALUE).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id("sendo_hamon_overdrive"))));
    public static final EntityType<HamonProjectileShieldEntity> SHIELD=Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id("hamon_projectile_shield"),
            EntityType.Builder.<HamonProjectileShieldEntity>of(HamonProjectileShieldEntity::new,MobCategory.MISC).sized(3,3).clientTrackingRange(8).updateInterval(Integer.MAX_VALUE).noSave().noSummon().fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id("hamon_projectile_shield"))));
    public static final EntityType<HamonBlockChargeEntity> BLOCK_CHARGE=Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id("hamon_block_charge"),
            EntityType.Builder.<HamonBlockChargeEntity>of(HamonBlockChargeEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(8).updateInterval(Integer.MAX_VALUE).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id("hamon_block_charge"))));
    public static final EntityType<HamonAfterimageEntity> AFTERIMAGE=Registry.register(BuiltInRegistries.ENTITY_TYPE,RipplesFabric.id("afterimage"),
            EntityType.Builder.<HamonAfterimageEntity>of(HamonAfterimageEntity::new,MobCategory.MISC).sized(.6F,1.8F).noSummon().clientTrackingRange(5).updateInterval(Integer.MAX_VALUE).dontTrackDeltas()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,RipplesFabric.id("afterimage"))));
    public static void initialize(){}
    private HamonEntities(){}
}
