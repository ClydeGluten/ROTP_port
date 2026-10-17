package com.github.standobyte.jojo.fabric.stand.magiciansred;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Separate registered source small-ankh type; its explosion bypasses ordinary damage cooldowns. */
public final class MRCrossfireSpecialEntity extends MRCrossfireEntity {
    public MRCrossfireSpecialEntity(EntityType<? extends MRCrossfireSpecialEntity> type,Level level){super(type,level);}
    public MRCrossfireSpecialEntity(MagiciansRedEntity owner){this(MagiciansRedActions.CROSSFIRE_SPECIAL_ENTITY,owner.level());initializeOwner(owner,false);}
    @Override public boolean small(){return true;}
}
