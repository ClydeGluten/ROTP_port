package com.github.standobyte.jojo.fabric.stand.magiciansred;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;

/** Active source fireball: collision damage and ten-second ignition; commented water handling stays inactive. */
public final class MRFireballEntity extends MRProjectileEntity {
    public MRFireballEntity(EntityType<? extends MRFireballEntity> type,Level level){super(type,level);}
    public MRFireballEntity(MagiciansRedEntity owner){this(MagiciansRedActions.FIREBALL_ENTITY,owner.level());initializeOwner(owner);}
    @Override public int lifespan(){return 100;}
    @Override protected float baseDamage(){return 2;}
    @Override protected float hardness(){return 5;}
    @Override protected int igniteSeconds(){return 10;}
    @Override public boolean isOnFire(){return true;}
    @Override protected void afterBlockHit(ServerLevel server,BlockHitResult hit,boolean destroyed){
        if(server.getGameRules().get(GameRules.MOB_GRIEFING))MRFlameEntity.placeFire(server,destroyed?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection()),getOwner());
    }
}
