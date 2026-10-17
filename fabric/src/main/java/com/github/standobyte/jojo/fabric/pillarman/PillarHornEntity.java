package com.github.standobyte.jojo.fabric.pillarman;

import com.github.standobyte.jojo.fabric.stand.OwnedBodyPartEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PillarHornEntity extends OwnedBodyPartEntity {
    public PillarHornEntity(EntityType<? extends PillarHornEntity> type, Level level) { super(type, level); }
    public PillarHornEntity(LivingEntity owner) { this(PillarBodyParts.HORN, owner.level()); initializeOwner(owner, 40); }
    @Override protected float baseDamage() { return .5F; }
    @Override protected boolean hurtThroughImmunity() { return true; }
    @Override public float knockbackMultiplier() { return 0; }
    @Override protected float maxHardness() { return 5; }
    @Override protected float movementSpeed() { return .4F; }
    @Override protected float retractSpeed() { return movementSpeed() * 3; }
    @Override protected int timeAtFullLength() { return 4; }
    @Override public boolean isBodyPart() { return true; }
    @Override protected Vec3 ownerRelativeOffset() { return new Vec3(0, .15F, 0); }
}
