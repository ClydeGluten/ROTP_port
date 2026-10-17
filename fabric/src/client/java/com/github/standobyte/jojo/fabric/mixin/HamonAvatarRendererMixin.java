package com.github.standobyte.jojo.fabric.mixin;
import com.github.standobyte.jojo.fabric.client.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
abstract class HamonAvatarRendererMixin extends LivingEntityRenderer<Avatar,AvatarRenderState,PlayerModel>{
    @Inject(method="getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",at=@At("HEAD"),cancellable=true)
    private static void jojo$tommyPose(Avatar entity,net.minecraft.world.item.ItemStack stack,net.minecraft.world.InteractionHand hand,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.model.HumanoidModel.ArmPose> ci){
        if(!entity.isSwinging() && stack.is(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.TOMMY_GUN))ci.setReturnValue(net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
    protected HamonAvatarRendererMixin(EntityRendererProvider.Context context,PlayerModel model,float shadow){super(context,model,shadow);}
    @Inject(method="<init>",at=@At("TAIL"))
    private void jojo$hamonLayer(EntityRendererProvider.Context context,boolean slim,CallbackInfo ci){addLayer(new HamonProtectionLayer(this));addLayer(new HamonMaskLayer(this));addLayer(new HamonBarrageLayer(this));addLayer(new com.github.standobyte.jojo.fabric.client.pillarman.PillarWindClient.CloakLayer(this));}
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
    private void jojo$hamonPose(Avatar entity,AvatarRenderState state,float partial,CallbackInfo ci){HamonPlayerAnimation.extract(entity,state,partial);HamonBarrageLayer.extract(entity,state,partial);
        com.github.standobyte.jojo.fabric.client.pillarman.PillarmanLayers.extract(entity,state);
        com.github.standobyte.jojo.fabric.client.pillarman.PillarmanPresentation.extract(entity,state,partial);
        com.github.standobyte.jojo.fabric.client.pillarman.PillarWindClient.extract(entity,state,partial);
        if(entity.getVehicle() instanceof com.github.standobyte.jojo.fabric.LeavesGliderEntity)state.isPassenger=false;
        var helmet=entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if(helmet.is(com.github.standobyte.jojo.fabric.HamonItems.BREATH_CONTROL_MASK) || helmet.is(com.github.standobyte.jojo.fabric.equipment.EquipmentContent.BLADE_HAT)){state.headEquipment=helmet.copy();state.headItem.clear();}}
}
