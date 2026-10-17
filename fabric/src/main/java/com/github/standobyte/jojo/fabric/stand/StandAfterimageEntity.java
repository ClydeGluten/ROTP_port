package com.github.standobyte.jojo.fabric.stand;

import com.github.standobyte.jojo.fabric.HamonAfterimageEntity;
import com.github.standobyte.jojo.fabric.HamonEntities;
import net.minecraft.world.entity.LivingEntity;

/** Shared source afterimage allocation; Hamon and Stands use one original entity registry entry. */
public final class StandAfterimageEntity {
    public static void initialize(){HamonAfterimageEntity.initialize();}
    public static void addAfterimages(LivingEntity entity,int count,int lifespan){HamonAfterimageEntity.addAfterimages(entity,count,lifespan);}
    private StandAfterimageEntity(){}
}
