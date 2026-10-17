package com.github.standobyte.jojo.fabric.stand.goldexperience;

import com.github.standobyte.jojo.fabric.RipplesFabric;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Potion cow products and inherited effects, applied after vanilla milk/stew consumption. */
public final class GEAnimalProducts {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<MobEffectInstance>> STREAM = StreamCodec.of((buffer, effects) -> {
        buffer.writeVarInt(effects.size()); effects.forEach(effect -> MobEffectInstance.STREAM_CODEC.encode(buffer, effect));
    }, buffer -> {
        int count = buffer.readVarInt(); if (count < 0 || count > 128) throw new DecoderException("Invalid GE product effects");
        var effects = new ArrayList<MobEffectInstance>();
        for (int i = 0; i < count; i++) effects.add(MobEffectInstance.STREAM_CODEC.decode(buffer));
        return List.copyOf(effects);
    });
    public static final DataComponentType<List<MobEffectInstance>> EFFECTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            RipplesFabric.id("ge_product_effects"), DataComponentType.<List<MobEffectInstance>>builder()
                    .persistent(MobEffectInstance.CODEC.listOf(0, 128)).networkSynchronized(STREAM).build());
    public static ItemStack decorate(LivingEntity animal, Player player, ItemStack product) {
        if (animal instanceof AgeableMob ageable && ageable.isBaby() || animal instanceof Leashable leashable && leashable.getLeashHolder() == player) return product;
        if (!product.is(Items.MILK_BUCKET) && !product.is(Items.MUSHROOM_STEW) && !product.is(Items.SUSPICIOUS_STEW)) return product;
        var effects = animal.getAttachedOrCreate(GoldExperienceTransformation.PRODUCT_EFFECTS);
        if (effects.isEmpty()) return product;
        product.set(EFFECTS, effects.stream().map(MobEffectInstance::new).toList()); return product;
    }
    /** Root invokes after the Consumable's onConsumeEffects and before its stack count is decremented. */
    public static void consume(LivingEntity user, ItemStack used) {
        if (user.level().isClientSide()) return;
        var effects = used.get(EFFECTS);
        if (effects != null) effects.forEach(effect -> user.addEffect(new MobEffectInstance(effect)));
    }
    public static void offspring(LivingEntity first, LivingEntity second, LivingEntity child) {
        var left = first.getAttachedOrCreate(GoldExperienceTransformation.PRODUCT_EFFECTS);
        var right = second.getAttachedOrCreate(GoldExperienceTransformation.PRODUCT_EFFECTS);
        if (left.isEmpty() && right.isEmpty()) return;
        var merged = new LinkedHashMap<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, MobEffectInstance>();
        left.forEach(effect -> merged.put(effect.getEffect(), new MobEffectInstance(effect)));
        right.forEach(effect -> merged.merge(effect.getEffect(), new MobEffectInstance(effect), (a, b) ->
                new MobEffectInstance(a.getEffect(), Math.max(a.getDuration(), b.getDuration()), Math.max(a.getAmplifier(), b.getAmplifier()))));
        child.setAttached(GoldExperienceTransformation.PRODUCT_EFFECTS, List.copyOf(merged.values()));
    }
    static void initialize() { }
    private GEAnimalProducts() { }
}
