package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.*;
import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/** Source horn/rib actions. Root dispatches validated enum intents and owns shared lifecycle events. */
public final class PillarBodyParts {
    public static final EntityType<PillarHornEntity> HORN = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("pm_horn"),
            EntityType.Builder.<PillarHornEntity>of(PillarHornEntity::new, MobCategory.MISC).sized(.125F, .125F).clientTrackingRange(8).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("pm_horn"))));
    public static final EntityType<PillarRibsEntity> RIBS = Registry.register(BuiltInRegistries.ENTITY_TYPE, RipplesFabric.id("pillarman_ribs"),
            EntityType.Builder.<PillarRibsEntity>of(PillarRibsEntity::new, MobCategory.MISC).sized(.35F, .35F).clientTrackingRange(8).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, RipplesFabric.id("pillarman_ribs"))));
    public static final net.minecraft.resources.Identifier HORN_ACTION = RipplesFabric.id("pillarman_horn_attack"), RIBS_ACTION = RipplesFabric.id("pillarman_ribs_blades");
    public static void initialize() { }
    public static int hornCooldown(LivingEntity player) { return AbilityCooldowns.remaining(player, HORN_ACTION); }
    public static int ribsCooldown(LivingEntity player) { return AbilityCooldowns.remaining(player, RIBS_ACTION); }
    public static boolean horn(ServerPlayer player) {
        if (!PillarmanBaseActions.canStart(player, 2, true, true) || hornCooldown(player) > 0 || !PillarmanService.spend(player, 15)) return false;
        player.level().addFreshEntity(new PillarHornEntity(player));
        AbilityCooldowns.start(player, HORN_ACTION, player.isCreative() ? 0 : 60);
        return true;
    }
    public static boolean ribs(ServerPlayer player) {
        if (!PillarmanBaseActions.canStart(player, 2, false, false) || ribsCooldown(player) > 0 || !PillarmanService.spend(player, 60)) return false;
        // MathUtil.xRotYRotOffsets(2*pi,10) returns pitch=acos(sqrt(100/101)), yaw=-0.
        float pitch = (float) Math.toDegrees(Math.acos(Math.sqrt(100D / 101D)));
        double[][] offsets = {{-.18,-.50},{-.22,-.60},{-.22,-.70},{-.18,-.80},{.18,-.50},{.22,-.65},{.22,-.85},{.18,-.95}};
        for (var offset : offsets) player.level().addFreshEntity(new PillarRibsEntity(player, pitch, 0, offset[0], offset[1]));
        // Unlike the ordinary cooldown(80) overload, source uses cooldown(80,0): technical even in creative.
        AbilityCooldowns.start(player, RIBS_ACTION, 80);
        return true;
    }
    /** Shared AbilityCooldowns ticks once for all players, including those without a Stand. */
    public static void tick(ServerPlayer player) { }
    public static void clear(LivingEntity owner) {
        if (owner instanceof ServerPlayer player) { AbilityCooldowns.start(player, HORN_ACTION, 0); AbilityCooldowns.start(player, RIBS_ACTION, 0); }
        if (owner.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var remove = new java.util.ArrayList<OwnedBodyPartEntity>();
            for (var entity : level.getAllEntities()) if (entity instanceof OwnedBodyPartEntity part && part.livingOwner() == owner) remove.add(part);
            remove.forEach(OwnedBodyPartEntity::discard);
        }
    }
    public static void clearAllRuntime() { }
    private PillarBodyParts() { }
}
