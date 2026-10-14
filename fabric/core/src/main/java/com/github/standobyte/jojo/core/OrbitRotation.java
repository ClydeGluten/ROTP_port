package com.github.standobyte.jojo.core;

/** Independent camera angles and camera-relative movement, with no world-target input. */
public final class OrbitRotation {
    private float yaw;
    private float pitch;

    public void reset(float yaw, float pitch) {
        this.yaw = wrap(yaw);
        this.pitch = Math.clamp(pitch, -90, 90);
    }

    public void turn(double horizontal, double vertical) {
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) return;
        reset(yaw + (float) (horizontal * 0.15), pitch + (float) (vertical * 0.15));
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public static float facing(float cameraYaw, float strafe, float forward) {
        return wrap(cameraYaw - (float) Math.toDegrees(Math.atan2(strafe, forward)));
    }

    /** Input components in the player's current frame, preserving the desired world-space direction. */
    public static float[] movement(float cameraYaw, float playerYaw, float strafe, float forward) {
        double angle = Math.toRadians(wrap(cameraYaw - playerYaw));
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new float[] {(float) (strafe * cos - forward * sin), (float) (forward * cos + strafe * sin)};
    }

    public static float wrap(float angle) {
        float wrapped = angle % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }
}
