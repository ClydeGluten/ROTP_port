package com.github.standobyte.jojo.fabric;

import com.github.standobyte.jojo.core.HamonProgress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.List;

/** Persistent original Hamon fields and separately retained one-time player notifications/rewards. */
public final class HamonAttachments {
    // The legacy constructor recomputes bonuses on load; the runtime cache is deliberately not saved.
    public static final Codec<HamonProgress> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("hamon",false).forGetter(HamonProgress::hamon),
            Codec.intRange(0,HamonProgress.MAX_HAMON_POINTS).optionalFieldOf("StrengthPoints",0).forGetter(HamonProgress::strengthPoints),
            Codec.intRange(0,HamonProgress.MAX_HAMON_POINTS).optionalFieldOf("ControlPoints",0).forGetter(HamonProgress::controlPoints),
            Codec.FLOAT.optionalFieldOf("PointsIncFrac",0F).forGetter(HamonProgress::pointsFraction),
            Codec.floatRange(0,100).optionalFieldOf("BreathingTechnique",0F).forGetter(HamonProgress::breathingLevel),
            Codec.FLOAT.optionalFieldOf("Energy",0F).forGetter(HamonProgress::energy),
            Codec.FLOAT.optionalFieldOf("BreathStability",1000F).forGetter(HamonProgress::breathStability),
            Codec.STRING.listOf().optionalFieldOf("Skills",List.of()).forGetter(HamonProgress::learnedSkills),
            Codec.STRING.optionalFieldOf("CharacterTechnique","").forGetter(HamonProgress::technique),
            Codec.INT.listOf().optionalFieldOf("Exercises",List.of(0,0,0,0)).forGetter(value->java.util.Arrays.stream(HamonProgress.Exercise.values()).map(value::exerciseTicks).toList()),
            Codec.FLOAT.optionalFieldOf("TrainingBonus",0F).forGetter(HamonProgress::trainingBonus),
            Codec.INT.optionalFieldOf("CanSkipDays",0).forGetter(HamonProgress::skipDays),
            Codec.INT.optionalFieldOf("EnergyTicks",0).forGetter(HamonProgress::energyTicks),
            Codec.INT.optionalFieldOf("MaskNoBreathTicks",0).forGetter(HamonProgress::maskTicks),
            Codec.INT.optionalFieldOf("NoBreathIncTicks",0).forGetter(HamonProgress::noRecoveryTicks),
            Codec.BOOL.optionalFieldOf("TCSA",true).forGetter(HamonProgress::tcsa)
    ).apply(i,HamonProgress::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,HamonProgress> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeBoolean(v.hamon());b.writeVarInt(v.strengthPoints());b.writeVarInt(v.controlPoints());b.writeFloat(v.pointsFraction());
        b.writeFloat(v.breathingLevel());b.writeFloat(v.energy());b.writeFloat(v.breathStability());
        b.writeVarInt(v.learnedSkills().size());for(var id:v.learnedSkills())b.writeUtf(id,128);b.writeUtf(v.technique(),128);
        for(var ex:HamonProgress.Exercise.values())b.writeVarInt(v.exerciseTicks(ex));
        b.writeFloat(v.trainingBonus());b.writeVarInt(v.skipDays());b.writeVarInt(v.energyTicks());b.writeVarInt(v.maskTicks());b.writeVarInt(v.noRecoveryTicks());b.writeBoolean(v.tcsa());
        b.writeVarInt(v.effectiveExerciseMask());
    },b->{boolean active=b.readBoolean();int strength=b.readVarInt(),control=b.readVarInt();float fraction=b.readFloat(),breathing=b.readFloat(),energy=b.readFloat(),stability=b.readFloat();
        int count=b.readVarInt();if(count<0 || count>1024)throw new IllegalArgumentException("Invalid Hamon skills");
        var skills=new java.util.ArrayList<String>();for(int j=0;j<count;j++)skills.add(b.readUtf(128));String technique=b.readUtf(128);
        return new HamonProgress(active,strength,control,fraction,breathing,energy,stability,skills,technique,
                List.of(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()),b.readFloat(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt());});
    public static final AttachmentType<HamonProgress> PROGRESS=AttachmentRegistry.<HamonProgress>builder().initializer(()->HamonProgress.EMPTY)
            .persistent(CODEC).copyOnDeath().syncWith(STREAM_CODEC,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("hamon_progress"));
    public static final AttachmentType<HamonHudState> HUD=AttachmentRegistry.<HamonHudState>builder().initializer(()->HamonHudState.EMPTY)
            .syncWith(HamonHudState.STREAM_CODEC,AttachmentSyncPredicate.all()).buildAndRegister(RipplesFabric.id("hamon_hud"));
    public static final AttachmentType<Boolean> SCARF_GIVEN=AttachmentRegistry.<Boolean>builder().initializer(()->false).persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("hamon_scarf_given"));
    public static final AttachmentType<Boolean> INTRO_READ=AttachmentRegistry.<Boolean>builder().initializer(()->false).persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("hamon_intro_read"));
    public static final AttachmentType<Boolean> LEARNING_NOTIFIED=AttachmentRegistry.<Boolean>builder().initializer(()->false).persistent(Codec.BOOL).copyOnDeath().buildAndRegister(RipplesFabric.id("hamon_learning_notified"));
    public static void initialize() { }
    private HamonAttachments() { }
}
