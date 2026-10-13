package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StarPlatinumEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local presentation only: player eye rays, server targeting and Stand positions stay authoritative. */
public final class StandCamera {
    public record Pose(Vec3 position, float yaw, float pitch) { }

    public static Pose shoulder(Camera camera, float partialTick) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || camera.entity() != client.player
                || client.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                || client.player.isSleeping() || client.player.isPassenger() || camera.isPanoramicMode()) return null;
        var stand = StandView.ownStand(client);
        if (stand == null || stand.manuallyControlled()) return null;

        // Stand idles on the player's right. Move to the other shoulder, after vanilla's rear-wall clipping.
        var shoulder = new Vec3(camera.leftVector()).scale(1.15);
        var up = new Vec3(camera.upVector()).scale(0.35);
        var eye = client.player.getEyePosition(partialTick);
        var desired = camera.position().add(shoulder).add(up);
        desired = clipCamera(client, camera.position(), desired);
        desired = clipCamera(client, eye, desired);

        // Converge on the player's actual ray so the screen centre still indicates the aimed block/entity.
        // This changes only camera rotation; it never redirects attacks or extends their reach.
        var direction = aimPoint(client, partialTick).subtract(desired);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        return new Pose(desired, yaw, pitch);
    }

    private static Vec3 aimPoint(Minecraft client, float partialTick) {
        var player = client.player;
        var eye = player.getEyePosition(partialTick);
        var ray = player.getViewVector(partialTick).scale(64);
        var block = player.pick(64, partialTick, false);
        var hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(ray),
                player.getBoundingBox().expandTowards(ray).inflate(1),
                entity -> !entity.isSpectator() && entity.isPickable()
                        && !(entity instanceof StarPlatinumEntity stand && stand.ownerId() == player.getId()),
                eye.distanceToSqr(block.getLocation()));
        return hit == null ? block.getLocation() : hit.getLocation();
    }

    private static Vec3 clipCamera(Minecraft client, Vec3 start, Vec3 end) {
        var movement = end.subtract(start);
        double length = movement.length();
        if (length < 1.0E-6) return start;
        double fraction = 1;
        // Sweep a small camera volume, not just its centre, to keep the near plane out of corners.
        for (int i = 0; i < 8; i++) {
            var offset = new Vec3((i & 1) == 0 ? -0.15 : 0.15,
                    (i & 2) == 0 ? -0.15 : 0.15, (i & 4) == 0 ? -0.15 : 0.15);
            var from = start.add(offset);
            var hit = client.level.clip(new ClipContext(from, end.add(offset),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != HitResult.Type.MISS) {
                fraction = Math.min(fraction, Math.max(0, (from.distanceTo(hit.getLocation()) - 0.05) / length));
            }
        }
        return start.add(movement.scale(fraction));
    }

    public static float opacity(StarPlatinumEntity stand) {
        var client = Minecraft.getInstance();
        if (client.player == null || stand.ownerId() != client.player.getId()) return 1;
        var camera = client.gameRenderer.mainCamera();
        if (camera.entity() == stand && client.options.getCameraType().isFirstPerson()) return 0;
        if (camera.entity() != client.player) return 1;
        if (client.options.getCameraType().isFirstPerson()) return 0.16F;
        // Walls can retract the shoulder camera. Fade the local Stand if it still crosses the centre view.
        var box = stand.getBoundingBox().inflate(0.3);
        var end = camera.position().add(new Vec3(camera.forwardVector()).scale(8));
        return box.contains(camera.position()) || box.clip(camera.position(), end).isPresent() ? 0.16F : 1;
    }

    private StandCamera() { }
}
