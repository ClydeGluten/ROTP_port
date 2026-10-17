package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StandManifestation;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Independent third-person orbit. Vanilla handles camera distance/walls; the server still validates all actions. */
public final class StandCamera {
    private static final com.github.standobyte.jojo.core.OrbitRotation orbit = new com.github.standobyte.jojo.core.OrbitRotation();
    private static net.minecraft.client.player.LocalPlayer owner;
    private static net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    private static CameraType previousType;
    private static int aimTicks;

    /** Camera lifecycle runs before vanilla reads player angles, including an F5 return to first person. */
    public static boolean update() {
        var c = Minecraft.getInstance();
        var type = c.options.getCameraType();
        boolean eligible = !com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.isPossessing() && c.player != null && c.level != null && c.player.isAlive() && !c.player.isSleeping()
                && c.getCameraEntity() == c.player && !c.gameRenderer.mainCamera().isPanoramicMode();
        if (!eligible || type.isFirstPerson()) {
            if (eligible && owner == c.player && previousType != null && !previousType.isFirstPerson()) {
                rotatePlayer(viewYaw(), viewPitch());
                owner.yRotO = owner.getYRot();
                owner.xRotO = owner.getXRot();
            }
            owner = null;
            ownerLevel = null;
            aimTicks = 0;
            previousType = type;
            return false;
        }
        if (owner != c.player || ownerLevel != c.level) {
            owner = c.player;
            ownerLevel = c.level;
            orbit.reset(owner.getYRot(), owner.getXRot());
            aimTicks = 0;
        }
        previousType = type;
        return true;
    }

    /** An original instant-time action may explicitly turn the user toward its target. */
    public static void adoptServerFacing(float yaw, float pitch) {
        if (!update()) return;
        boolean front = previousType == CameraType.THIRD_PERSON_FRONT;
        orbit.reset(yaw - (front ? 180 : 0), front ? -pitch : pitch);
        aimTicks = 0;
    }

    public static float yaw() { return orbit.yaw(); }
    public static float pitch() { return orbit.pitch(); }
    public static float viewYaw() { return orbit.yaw() + (previousType == CameraType.THIRD_PERSON_FRONT ? 180 : 0); }
    public static float viewPitch() { return previousType == CameraType.THIRD_PERSON_FRONT ? -orbit.pitch() : orbit.pitch(); }

    /** Consume vanilla sensitivity-adjusted mouse deltas; player facing is independent while looking around. */
    public static boolean turn(net.minecraft.client.player.LocalPlayer player, double horizontal, double vertical) {
        if (!update() || player != owner) return false;
        orbit.turn(horizontal, vertical);
        return true;
    }

    public static net.minecraft.world.phys.Vec2 movement(net.minecraft.world.phys.Vec2 input) {
        if (!update()) return input;
        var c = Minecraft.getInstance();
        var stand = StandView.ownManifestation(c);
        boolean aiming = aimTicks > 0 || c.options.keyAttack.isDown() || c.options.keyUse.isDown()
                || owner.isUsingItem() || (stand != null && stand.actionPhase() != 0);
        if (aimTicks > 0) aimTicks--;
        if (aiming || owner.isPassenger() || owner.isSwimming() || owner.isFallFlying()) {
            rotatePlayer(viewYaw(), viewPitch());
            return input;
        }
        if (input.lengthSquared() < 1.0E-8F) return input;
        rotatePlayer(com.github.standobyte.jojo.core.OrbitRotation.facing(viewYaw(), input.x, input.y), 0);
        float[] relative = com.github.standobyte.jojo.core.OrbitRotation.movement(viewYaw(), owner.getYRot(), input.x, input.y);
        return new net.minecraft.world.phys.Vec2(relative[0], relative[1]);
    }

    /** Send ordinary rotation before the action packet so server-side hits use the visible aiming direction. */
    public static void aimForAction() {
        if (!update()) return;
        aimTicks = 5;
        rotatePlayer(viewYaw(), viewPitch());
        owner.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(
                owner.getYRot(), owner.getXRot(), owner.onGround(), owner.horizontalCollision));
    }

    private static void rotatePlayer(float yaw, float pitch) {
        if (owner == null) return;
        // Keep interpolation on the short side of the angle boundary. Do not alter the camera orbit.
        float delta = com.github.standobyte.jojo.core.OrbitRotation.wrap(yaw - owner.getYRot());
        owner.setYRot(owner.getYRot() + delta);
        owner.setXRot(pitch);
    }

    public static float opacity(StandManifestation stand) {
        var client = Minecraft.getInstance();
        if (client.player == null || stand.ownerId() != client.player.getId()) return 1;
        var camera = client.gameRenderer.mainCamera();
        if (camera.entity() == stand.entity() && client.options.getCameraType().isFirstPerson()) return 0;
        if (camera.entity() != client.player) return 1;
        if (client.options.getCameraType().isFirstPerson()) return 0.16F;
        // Walls can retract the camera. Fade the local Stand if it crosses the centre view.
        var box = stand.entity().getBoundingBox().inflate(0.3);
        var end = camera.position().add(new Vec3(camera.forwardVector()).scale(8));
        return box.contains(camera.position()) || box.clip(camera.position(), end).isPresent() ? 0.16F : 1;
    }

    private StandCamera() { }
}
