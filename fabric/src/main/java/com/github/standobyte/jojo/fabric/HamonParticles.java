package com.github.standobyte.jojo.fabric;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
/** Original Hamon aura and the three face-constrained Sendo spark planes. */
public final class HamonParticles {
    public static final SimpleParticleType AURA=register("hamon_aura"), AURA_BLUE=register("hamon_aura_blue"), AURA_YELLOW=register("hamon_aura_yellow"), AURA_RED=register("hamon_aura_red"), AURA_SILVER=register("hamon_aura_silver");
    public static final SimpleParticleType SPARK_BLUE=register("hamon_spark_blue"), SPARK_YELLOW=register("hamon_spark_yellow"), SPARK_RED=register("hamon_spark_red"), SPARK_SILVER=register("hamon_spark_silver");
    public static final SimpleParticleType SENDO_X=register("sendo_spark_x"),SENDO_Y=register("sendo_spark_y"),SENDO_Z=register("sendo_spark_z");
    private static SimpleParticleType register(String id){return Registry.register(BuiltInRegistries.PARTICLE_TYPE,RipplesFabric.id(id),FabricParticleTypes.simple(true));}
    public static void initialize(){}
    private HamonParticles(){}
}
