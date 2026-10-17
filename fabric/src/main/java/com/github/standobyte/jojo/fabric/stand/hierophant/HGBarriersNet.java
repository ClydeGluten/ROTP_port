package com.github.standobyte.jojo.fabric.stand.hierophant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;

/** Source barrier graph: separated closest firing points, one funded firing batch per server tick. */
public final class HGBarriersNet {
    private final Map<HGBarrierEntity, Points> barriers = new LinkedHashMap<>();
    private boolean canShoot;
    public void tick() { barriers.keySet().removeIf(barrier -> !barrier.isAlive() || barrier.wasRipped()); canShoot = true; }
    public int getSize() { return barriers.size(); }
    public List<HGBarrierEntity> barriers() { return List.copyOf(barriers.keySet()); }
    public Stream<Vec3> wasRippedAt() { return barriers.keySet().stream().flatMap(barrier -> barrier.wasRippedAt().stream()); }
    public void clear() { for (var barrier : List.copyOf(barriers.keySet())) barrier.discard(); barriers.clear(); }
    public void add(HGBarrierEntity barrier) {
        if (barriers.size() >= 100 || !barrier.isAlive()) return;
        Vec3 start = barrier.position(), delta = barrier.origin(1).subtract(start);
        if (!Double.isFinite(delta.lengthSqr())) return;
        barriers.put(barrier, delta.lengthSqr() <= 256 ? new Points(start.add(delta.scale(.5)), Vec3.ZERO, 1)
                : new Points(start.add(delta.normalize().scale(8)), delta.normalize().scale(8), (int) Math.floor(delta.length() / 8)));
    }
    public int shootEmeraldsFromBarriers(HierophantGreenEntity stand, Vec3 target, int tick,
            double maximumPoints, float staminaPerPoint, double minimumGap, boolean breakBlocks) {
        if (!canShoot || stand.owner() == null || stand.level().isClientSide() || !Double.isFinite(maximumPoints)) return 0;
        var remaining = barriers.values().stream().map(Points::copy).toList();
        var chosen = new ArrayList<Vec3>();
        int count = Math.min(20, HierophantGreenActions.fractionCount(stand.getRandom(), Math.max(0, maximumPoints)));
        for (int i = 0; i < count; i++) {
            Vec3 point = null; double nearest = Double.POSITIVE_INFINITY;
            for (var segment : remaining) {
                Vec3 candidate = segment.nearest(target);
                if (candidate != null && candidate.distanceToSqr(target) < nearest) { point = candidate; nearest = candidate.distanceToSqr(target); }
            }
            if (point == null) break;
            chosen.add(point); for (var segment : remaining) segment.exclude(point, minimumGap);
        }
        int spawned = 0;
        for (Vec3 point : chosen) {
            if (!HierophantGreenActions.consumeStamina(stand.owner(), staminaPerPoint)) break;
            int batch = HierophantGreenActions.fractionCount(stand.getRandom(), stand.projectileFireRate());
            for (int i = 0; i < batch; i++) {
                HGEmeraldEntity emerald = new HGEmeraldEntity(stand, false);
                emerald.setPos(point); emerald.setBreakBlocks(breakBlocks); emerald.setLowerKnockback(true);
                Vec3 direction = target.subtract(point);
                emerald.shoot(direction.x, direction.y, direction.z, 1.5F, stand.projectileInaccuracy(2));
                emerald.setDamageFactor(.75F * stand.projectileDamageFactor());
                stand.level().addFreshEntity(emerald); spawned++;
                if (tick == 0) stand.playOriginalAt("hierophant_green_emerald_splash", point);
            }
        }
        canShoot = false; return spawned;
    }
    /** Analytic form of the original eight-block point list. Exclusion intervals replace its quadratic adjacency graph. */
    private static final class Points {
        private record Interval(int first, int last) { }
        final Vec3 start, step; final int count;
        final List<Interval> excluded = new ArrayList<>();
        Points(Vec3 start, Vec3 step, int count) { this.start = start; this.step = step; this.count = count; }
        Points copy() { return new Points(start, step, count); }
        Vec3 point(int index) { return start.add(step.scale(index)); }
        boolean allowed(int index) { return index >= 0 && index < count && excluded.stream().noneMatch(interval -> index >= interval.first && index <= interval.last); }
        Vec3 nearest(Vec3 target) {
            double projected = step.lengthSqr() == 0 ? 0 : target.subtract(start).dot(step) / step.lengthSqr();
            int center = (int) Math.clamp(Math.round(projected), 0, count - 1);
            var candidates = new ArrayList<Integer>(); candidates.add(center); candidates.add(0); candidates.add(count - 1);
            for (var interval : excluded) { candidates.add(interval.first - 1); candidates.add(interval.last + 1); }
            Vec3 best = null; double distance = Double.POSITIVE_INFINITY;
            for (int index : candidates) if (allowed(index)) {
                Vec3 candidate = point(index); double next = candidate.distanceToSqr(target);
                if (next < distance) { best = candidate; distance = next; }
            }
            return best;
        }
        void exclude(Vec3 point, double gap) {
            if (step.lengthSqr() == 0) { if (start.distanceToSqr(point) < gap * gap || start.equals(point)) excluded.add(new Interval(0, 0)); return; }
            double center = point.subtract(start).dot(step) / step.lengthSqr();
            double perpendicular = start.add(step.scale(center)).distanceToSqr(point);
            double radiusSquared = gap * gap - perpendicular; if (radiusSquared <= 0) return;
            double radius = Math.sqrt(radiusSquared / step.lengthSqr());
            int first = Math.max(0, (int) Math.floor(center - radius) + 1), last = Math.min(count - 1, (int) Math.ceil(center + radius) - 1);
            if (first <= last) excluded.add(new Interval(first, last));
        }
    }
}
