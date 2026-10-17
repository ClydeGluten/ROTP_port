package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.VampirePresentation;
import com.github.standobyte.jojo.fabric.client.VampireRenderData;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
abstract class VampireRenderStateMixin implements VampireRenderData {
    @Unique private VampirePresentation.Visual jojo$visual=VampirePresentation.Visual.EMPTY;
    @Unique private float jojo$partial;
    public VampirePresentation.Visual jojo$vampireVisual(){return jojo$visual;}
    public void jojo$vampireVisual(VampirePresentation.Visual value){jojo$visual=value;}
    public float jojo$vampirePartialTick(){return jojo$partial;}
    public void jojo$vampirePartialTick(float value){jojo$partial=value;}
}
