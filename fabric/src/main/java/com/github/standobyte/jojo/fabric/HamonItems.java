package com.github.standobyte.jojo.fabric;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.function.Function;

/** Original Hamon equipment, consumables and Life Magnetism item/block handling. */
public final class HamonItems {
    public static final int MAX_AMMO = 500, MAX_OIL_USES = 120;
    public static final float GLIDER_MAX_ENERGY = 200;
    public static final Item BREATH_CONTROL_MASK = item("breath_control_mask", p -> new BreathControlMaskItem(p.durability(110)
            .equippable(EquipmentSlot.HEAD).enchantable(9).repairable(Items.IRON_INGOT)
            .attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                    new AttributeModifier(RipplesFabric.id("breath_control_mask_armor"),1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.HEAD).build())));
    public static final Item MOLOTOV = item("molotov", p -> new MolotovItem(p.stacksTo(16)));
    public static final Item METEORIC_SCRAP = item("meteoric_scrap", Item::new);
    public static final Item GLOVES = item("gloves", p -> new Item(p.stacksTo(1).enchantable(15).attributes(gloveAttributes())));
    public static final Item BUBBLE_GLOVES = item("bubble_gloves", p -> new BubbleGlovesItem(p.stacksTo(1).enchantable(15).attributes(gloveAttributes())));
    public static final Item SOAP = item("soap", p -> new SoapItem(p.stacksTo(1).craftRemainder(Items.GLASS_BOTTLE)));
    public static final Item OIL = item("oil", p -> new HamonOilItem(p.stacksTo(1)));
    public static final Item CLACKERS = item("clackers", p -> new ClackersItem(p.stacksTo(1).attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_SPEED,new AttributeModifier(RipplesFabric.id("clackers_attack_speed"),6,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build())));
    private static ItemAttributeModifiers gloveAttributes(){return ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build();}
    private static Item item(String id, Function<Item.Properties,Item> factory) {
        var key=ResourceKey.create(Registries.ITEM,RipplesFabric.id(id));
        return Registry.register(BuiltInRegistries.ITEM,key,factory.apply(new Item.Properties().setId(key)));
    }
    public static int value(ItemStack stack,String key) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getIntOr(key,0); }
    public static void value(ItemStack stack,String key,int amount) {
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag -> { if(amount>0) tag.putInt(key,amount); else tag.remove(key); });
    }
    public static int ammo(ItemStack stack) {return value(stack,"Ammo");}
    public static boolean consumeAmmo(ItemStack gloves,int amount,LivingEntity user) {
        int previous=ammo(gloves); value(gloves,"Ammo",Math.max(0,previous-amount));
        if(ammo(gloves)<=0) reload(gloves,user,null);
        return previous>0;
    }
    public static boolean reload(ItemStack gloves,Entity entity,ItemStack supplied) {
        if(ammo(gloves)>=MAX_AMMO || !(entity instanceof Player player))return false;
        ItemStack soap=supplied==null?find(player,s->s.is(SOAP)):supplied;
        if(!player.isCreative()) { if(soap.isEmpty())return false; soap.shrink(1); give(player,new ItemStack(Items.GLASS_BOTTLE)); }
        if(!entity.level().isClientSide())value(gloves,"Ammo",MAX_AMMO);
        return true;
    }
    public static ItemStack find(Player player,java.util.function.Predicate<ItemStack> predicate) {
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            ItemStack stack=player.getInventory().getItem(i);if(!stack.isEmpty()&&predicate.test(stack))return stack;
        }
        return ItemStack.EMPTY;
    }
    public static void give(LivingEntity entity,ItemStack stack) {
        if(entity instanceof Player player && player.getInventory().add(stack))return;
        if(entity.level() instanceof ServerLevel level)entity.spawnAtLocation(level,stack);
    }
    public enum SoapSource {BOTTLE,GLOVES,NONE}
    public static ItemStack soapItem(LivingEntity user) {
        for(ItemStack stack:new ItemStack[]{user.getMainHandItem(),user.getOffhandItem()})
            if(stack.is(SOAP)||stack.is(BUBBLE_GLOVES))return stack;
        return ItemStack.EMPTY;
    }
    // Original consumes the last partial glove charge too; the requested amount is not a minimum.
    public static boolean soapHasAmmo(LivingEntity user,int amount) {
        ItemStack item=soapItem(user);return item.is(SOAP)||item.is(BUBBLE_GLOVES)&&ammo(item)>0;
    }
    public static SoapSource consumeSoap(LivingEntity user,int glovesAmmo) {
        ItemStack soap=soapItem(user);
        if(soap.is(SOAP)){soap.shrink(1);give(user,new ItemStack(Items.GLASS_BOTTLE));return SoapSource.BOTTLE;}
        if(soap.is(BUBBLE_GLOVES)){consumeAmmo(soap,glovesAmmo,user);return SoapSource.GLOVES;}
        return SoapSource.NONE;
    }
    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty()&&!stack.is(GLOVES)&&!stack.is(BUBBLE_GLOVES)
                && stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE,0,EquipmentSlot.MAINHAND)>0;
    }
    public static boolean isLeaves(ItemStack stack) {return stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof LeavesBlock;}
    public static boolean lifeMagnetism(ServerPlayer user,BlockHitResult hit,float energy) {
        var level=user.level(); var state=hit==null?null:level.getBlockState(hit.getBlockPos());
        boolean block=state!=null&&state.getBlock() instanceof LeavesBlock;
        ItemStack leaves=ItemStack.EMPTY;
        if(!block){leaves=isLeaves(user.getMainHandItem())?user.getMainHandItem():isLeaves(user.getOffhandItem())?user.getOffhandItem():find(user,HamonItems::isLeaves);
            if(leaves.isEmpty())return false;state=((BlockItem)leaves.getItem()).getBlock().defaultBlockState();}
        Vec3 position=block?Vec3.atBottomCenterOf(hit.getBlockPos()):user.position();
        if(block&&!level.destroyBlock(hit.getBlockPos(),false,user))return false;
        var glider=new LeavesGliderEntity(HamonContentEntities.LEAVES_GLIDER,level);
        glider.setPos(position);glider.setYRot(user.getYRot());glider.setXRot(user.getXRot());
        glider.setLeavesBlock(state);glider.setEnergy(Math.min(energy,GLIDER_MAX_ENERGY));level.addFreshEntity(glider);
        if(!block){user.startRiding(glider);if(!user.isCreative())leaves.shrink(1);}
        VampireHamonParticles.emit(glider,.1F);return true;
    }
    public static void initialize() {
        net.minecraft.world.level.block.DispenserBlock.registerProjectileBehavior(MOLOTOV);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output->{
            output.accept(BREATH_CONTROL_MASK);output.accept(GLOVES);output.accept(CLACKERS);
            ItemStack gloves=new ItemStack(BUBBLE_GLOVES);value(gloves,"Ammo",MAX_AMMO);output.accept(gloves);
            output.accept(SOAP);output.accept(OIL);output.accept(MOLOTOV);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim,source,amount)->{
            if(!source.is(DamageTypeTags.BYPASSES_ARMOR)&&!source.getMsgId().startsWith("hamon")
                    &&source.getEntity()==source.getDirectEntity()&&source.getEntity() instanceof LivingEntity user
                    &&user.level() instanceof ServerLevel level&&HamonService.hasHamon(user)) {
                ItemStack weapon=user.getMainHandItem();int uses=value(weapon,"HamonOiled");
                if(uses>0&&HamonService.state(user).energy()>=500){HamonService.consumeResourceEnergy(user,500);
                    VampireHamon.damage(level,victim,user,1.5F);HamonService.gainActionPoints(user,true,500);value(weapon,"HamonOiled",uses-1);}
            }
            return true;
        });
    }
    private HamonItems(){}
}
