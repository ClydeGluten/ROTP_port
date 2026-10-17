package com.github.standobyte.jojo.fabric;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
/** Persisted charge. Remaining ticks survive chunk unload and dimension transfer. */
public record HamonCharge(float damage,int initial,int remaining,String owner,float spent,boolean gavePoints,boolean large,boolean projectile,boolean multiply) {
    public static final Codec<HamonCharge> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.FLOAT.fieldOf("damage").forGetter(HamonCharge::damage),Codec.INT.fieldOf("initial").forGetter(HamonCharge::initial),Codec.INT.fieldOf("remaining").forGetter(HamonCharge::remaining),Codec.STRING.fieldOf("owner").forGetter(HamonCharge::owner),Codec.FLOAT.fieldOf("spent").forGetter(HamonCharge::spent),Codec.BOOL.fieldOf("points").forGetter(HamonCharge::gavePoints),Codec.BOOL.fieldOf("large").forGetter(HamonCharge::large),Codec.BOOL.fieldOf("projectile").forGetter(HamonCharge::projectile),Codec.BOOL.fieldOf("multiply").forGetter(HamonCharge::multiply)).apply(i,HamonCharge::new));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,HamonCharge> STREAM_CODEC=net.minecraft.network.codec.StreamCodec.of(
        (b,c)->{b.writeFloat(c.damage);b.writeVarInt(c.initial);b.writeVarInt(c.remaining);b.writeUtf(c.owner,36);b.writeFloat(c.spent);b.writeBoolean(c.gavePoints);b.writeBoolean(c.large);b.writeBoolean(c.projectile);b.writeBoolean(c.multiply);},
        b->new HamonCharge(b.readFloat(),b.readVarInt(),b.readVarInt(),b.readUtf(36),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
    public HamonCharge tick(){return remaining==Integer.MAX_VALUE?this:new HamonCharge(damage,initial,remaining-1,owner,spent,gavePoints,large,projectile,multiply);}
    public HamonCharge ticks(int ticks){return new HamonCharge(damage,initial,ticks,owner,spent,gavePoints,large,projectile,multiply);}
    public HamonCharge hit(boolean keepLarge){return new HamonCharge(damage,initial,remaining,owner,spent,true,keepLarge,projectile,multiply);}
    public float projectileDamage(){return 3*damage*(initial==Integer.MAX_VALUE?1:Math.clamp((float)remaining/Math.max(initial,1),.25F,1));}
}
