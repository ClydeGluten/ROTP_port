package com.github.standobyte.jojo.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** UV-preserving hinge adapter for KosmX bend channels on the current rigid player model. */
public final class VampireBendGeometry {
    private record Face(List<ModelPart.Vertex> vertices, Vector3f normal, boolean moving) { }
    private static final class Bend {
        float angle, pivot; boolean upper; List<Face> faces;
    }
    private static final Map<ModelPart.Cube, Bend> BENDS = new WeakHashMap<>();
    public static void reset(PlayerModel model) {
        model.root().visit(new PoseStack(), (pose, path, index, cube) -> { var bend = BENDS.get(cube); if (bend != null) bend.angle = 0; });
    }
    public static void set(ModelPart part, float pivot, float angle, boolean upper) {
        // Only direct cubes; outer skin parts are handled separately with their own UVs.
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (!path.isEmpty()) return;
            var bend = BENDS.computeIfAbsent(cube, ignored -> new Bend());
            if (bend.faces == null) {
                bend.pivot = pivot; bend.upper = upper; bend.faces = new ArrayList<>();
                for (var polygon : cube.polygons) {
                    var vertices = List.of(polygon.vertices());
                    var low = clip(vertices, pivot, true); var high = clip(vertices, pivot, false);
                    if (low.size() >= 3) bend.faces.add(new Face(low, new Vector3f(polygon.normal()), upper));
                    if (high.size() >= 3) bend.faces.add(new Face(high, new Vector3f(polygon.normal()), !upper));
                }
            }
            bend.angle = angle;
        });
    }
    private static List<ModelPart.Vertex> clip(List<ModelPart.Vertex> vertices, float pivot, boolean low) {
        var result = new ArrayList<ModelPart.Vertex>();
        ModelPart.Vertex previous = vertices.getLast(); boolean previousInside = low ? previous.y() <= pivot : previous.y() >= pivot;
        for (var current : vertices) {
            boolean inside = low ? current.y() <= pivot : current.y() >= pivot;
            if (inside != previousInside) {
                float t = (pivot - previous.y()) / (current.y() - previous.y());
                result.add(new ModelPart.Vertex(lerp(previous.x(), current.x(), t), pivot, lerp(previous.z(), current.z(), t),
                        lerp(previous.u(), current.u(), t), lerp(previous.v(), current.v(), t)));
            }
            if (inside) result.add(current);
            previous = current; previousInside = inside;
        }
        return result;
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    /** Return true only for an active bend; every other vanilla cube keeps its original fast render path. */
    public static boolean compile(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, int color) {
        var bend = BENDS.get(cube);
        if (bend == null || Math.abs(bend.angle) < 1E-6) return false;
        float sine = (float) Math.sin(bend.angle), cosine = (float) Math.cos(bend.angle);
        for (var face : bend.faces) {
            var normal = new Vector3f(face.normal);
            if (face.moving) normal.rotateX(bend.angle);
            // Clipping an axis-aligned cube face produces triangles or quads.
            for (int i = 0; i < 4; i++) {
                var vertex = face.vertices.get(Math.min(i, face.vertices.size() - 1));
                float y = vertex.y(), z = vertex.z();
                if (face.moving) { float localY = y - bend.pivot; y = bend.pivot + localY * cosine - z * sine; z = localY * sine + z * cosine; }
                vertices.addVertex(pose, vertex.x() / 16, y / 16, z / 16).setColor(color).setUv(vertex.u(), vertex.v())
                        .setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
            }
        }
        return true;
    }
    private VampireBendGeometry() { }
}
