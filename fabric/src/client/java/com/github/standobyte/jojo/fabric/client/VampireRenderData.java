package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.VampirePresentation;

public interface VampireRenderData {
    VampirePresentation.Visual jojo$vampireVisual();
    void jojo$vampireVisual(VampirePresentation.Visual value);
    float jojo$vampirePartialTick();
    void jojo$vampirePartialTick(float value);
}
