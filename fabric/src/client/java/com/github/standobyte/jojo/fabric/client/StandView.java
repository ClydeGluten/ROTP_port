package com.github.standobyte.jojo.fabric.client;

import com.github.standobyte.jojo.fabric.StarPlatinumEntity;
import net.minecraft.client.Minecraft;

/** Camera state is derived from the owning player's synchronized manifestation. */
public final class StandView {
    private static boolean controlling;
    private static float zoom = 1;
    private static net.minecraft.world.phys.Vec3 bodyAnchor;
    private static net.minecraft.client.player.LocalPlayer anchoredPlayer;
    public static StarPlatinumEntity ownStand(Minecraft client) {
        return ownManifestation(client) instanceof StarPlatinumEntity stand ? stand : null;
    }
    public static com.github.standobyte.jojo.fabric.StandManifestation ownManifestation(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof com.github.standobyte.jojo.fabric.StandManifestation stand && !stand.entity().isRemoved() && stand.ownerId() == client.player.getId()) return stand;
        }
        return null;
    }
    public static void tick(Minecraft client, boolean active) {
        if (com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.isPossessing()) {
            controlling = false; bodyAnchor = null; anchoredPlayer = null; zoom = 1; return;
        }
        var stand = ownManifestation(client);
        boolean control = active && stand != null && stand.manuallyControlled();
        if (control) client.setCameraEntity(stand.entity());
        else if (controlling && client.player != null) client.setCameraEntity(client.player);
        controlling = control;
        boolean lockBody = active && stand != null && (stand.manuallyControlled() || stand.actionPhase() == StarPlatinumEntity.PHASE_TIME_CHARGE);
        if (lockBody) {
            if (bodyAnchor == null || anchoredPlayer != client.player) { bodyAnchor = client.player.position(); anchoredPlayer = client.player; }
            client.player.setPos(bodyAnchor); client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        } else { bodyAnchor = null; anchoredPlayer = null; }
        boolean zooming = active && stand != null && stand.actionPhase() == StarPlatinumEntity.PHASE_ZOOM;
        zoom = zooming ? Math.min(60, zoom + 1) : Math.max(1, zoom - 6);
        if (client.player == null) zoom = 1;
    }
    public static boolean bodyLocked() {
        if (com.github.standobyte.jojo.fabric.client.pillarman.PossessionClient.isPossessing()) return false;
        var stand = ownManifestation(Minecraft.getInstance());
        return stand != null && (stand.manuallyControlled() || stand.actionPhase() == StarPlatinumEntity.PHASE_TIME_CHARGE);
    }
    public static float zoom() { return zoom; }
    private StandView() { }
}
