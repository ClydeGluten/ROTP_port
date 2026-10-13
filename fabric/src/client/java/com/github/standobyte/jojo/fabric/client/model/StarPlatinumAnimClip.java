package com.github.standobyte.jojo.fabric.client.model;

/**
 * Minimal keyframe clip evaluator for data extracted from assets/jojo/animations/star_platinum.animation.json
 * (see fabric/tools/generate_star_platinum_model.py). Supports rotation (degrees) and position channels with the
 * per-keyframe easing used by the original files. No general MoLang: channels with query expressions are not exported
 * as keyframes, except the narrow {@link LookBinding} form (a rotation slot that is exactly
 * {@code query.head_x_rotation} / {@code query.head_y_rotation}); the {@code head} bone is bound by the model itself.
 *
 * <p>Part of the Fabric port of Ripples of the Past (GPL-3.0).
 */
final class StarPlatinumAnimClip {
    private StarPlatinumAnimClip() {}

    /** @param position {@code true}: translation in model units; {@code false}: rotation in degrees. */
    record Channel(String bone, boolean position, float[] times, float[][] values, String[] easings) {
        /** As in the original (Mojang-style) animator: the easing of the END keyframe shapes the segment. */
        void sample(float time, float[] out) {
            int last = times.length - 1;
            if (time <= times[0] || last == 0) {
                System.arraycopy(values[0], 0, out, 0, 3);
                return;
            }
            if (time >= times[last]) {
                System.arraycopy(values[last], 0, out, 0, 3);
                return;
            }
            int i = 0;
            while (i + 1 < last && time >= times[i + 1]) i++;
            float alpha = (time - times[i]) / (times[i + 1] - times[i]);
            alpha = ease(easings[i + 1], alpha);
            for (int k = 0; k < 3; k++) {
                out[k] = values[i][k] + (values[i + 1][k] - values[i][k]) * alpha;
            }
        }
    }

    /**
     * A rotation channel of the original whose keyframes are constant except for one or two slots that are exactly
     * {@code query.head_x_rotation} (look pitch, degrees) or {@code query.head_y_rotation} (look yaw, degrees).
     *
     * @param constant    the numeric (degrees) value of the slots that are not bound to a query
     * @param pitchSlot   rotation slot (0..2) bound to the look pitch, or -1
     * @param yawSlot     rotation slot (0..2) bound to the look yaw, or -1
     * @param activeUntil last keyframe time (seconds) that still holds the query; later keyframes (a blend back to a
     *                    numeric pose) are NOT reproduced, the binding simply stops applying after this time
     */
    record LookBinding(String bone, float[] constant, int pitchSlot, int yawSlot, float activeUntil) {}

    /**
     * @param phaseStarts start time (seconds) of windup, perform, recovery as given by the clip's timeline, or
     *                    empty for looping clips. Phases the timeline does not mention default to 0.
     * @param loopBackTo  the original's {@code phase.loopBack} value of the PERFORM timeline entry, or {@code -1}
     *                    when the clip does not loop back. The loop region is PERFORM start .. RECOVERY start.
     * @param looks       channels bound to the look direction, see {@link LookBinding}
     */
    record Clip(String name, float length, boolean loop, float[] phaseStarts, float loopBackTo, LookBinding[] looks,
                Channel[] channels) {
        float wrap(float seconds) {
            if (loop) {
                float t = seconds % length;
                return t < 0 ? t + length : t;
            }
            return Math.max(0, Math.min(length, seconds));
        }

        boolean hasLoopBack() {
            return loopBackTo >= 0;
        }

        /**
         * The original's {@code LOOP_BACK} time mode: {@code performStart + t % (recoveryStart - loopBackTo)}.
         * Only valid for clips with {@link #hasLoopBack()}.
         */
        float wrapLoopBack(float seconds) {
            float loopLength = phaseStarts[2] - loopBackTo;
            return phaseStarts[1] + Math.max(0, seconds) % loopLength;
        }
    }

    static float ease(String name, float t) {
        return switch (name) {
            case "easeInSine" -> sine(t);
            case "easeOutSine" -> 1 - sine(1 - t);
            case "easeInOutSine" -> t < 0.5F ? sine(t * 2) / 2 : 1 - sine((1 - t) * 2) / 2;
            case "easeInQuad" -> t * t;
            case "easeOutQuad" -> 1 - (1 - t) * (1 - t);
            case "easeInCubic" -> t * t * t;
            case "easeOutCubic" -> 1 - (1 - t) * (1 - t) * (1 - t);
            default -> t; // linear / unknown
        };
    }

    private static float sine(float t) {
        return 1 - (float) Math.cos(t * Math.PI / 2);
    }
}
