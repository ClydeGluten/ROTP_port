package com.github.standobyte.jojo.fabric.mixin;

import com.github.standobyte.jojo.fabric.client.FrozenLayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

@Mixin(ModelPart.Cube.class)
abstract class FrozenModelCubeMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void jojo$modelTextureSize(int u, int v, float x, float y, float z, float dx, float dy, float dz,
            float growX, float growY, float growZ, boolean mirror, float width, float height, Set<Direction> faces, CallbackInfo ci) {
        FrozenLayer.rememberCube((ModelPart.Cube) (Object) this, width, height);
    }
}
