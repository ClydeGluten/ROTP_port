package com.github.standobyte.jojo.fabric.client.model;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for {@link StarPlatinumModel}. Populated by the renderer (outside this package).
 *
 * <p>Part of the Fabric port of Ripples of the Past (GPL-3.0).
 */
public class StarPlatinumRenderState extends LivingEntityRenderState {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_WINDUP = 1;
    public static final int PHASE_PERFORM = 2;
    public static final int PHASE_RECOVERY = 3;
    /** Looping barrage (the original's barrage animation, PERFORM phase). */
    public static final int PHASE_BARRAGE = 4;
    /** Holding the guard (the original's {@code block} pose). */
    public static final int PHASE_GUARD = 5;
    /** The guard was just broken: a mild stagger, not a punch animation. */
    public static final int PHASE_GUARD_BROKEN = 6;
    /** Star Finger pointing pose (duration 20 ticks). */
    public static final int PHASE_STAR_FINGER = 7;
    /** Inhale pose (duration 80 ticks). */
    public static final int PHASE_INHALE = 8;
    /** Zoom mode (indefinite idle gaze). */
    public static final int PHASE_ZOOM = 9;
    /** Time Stop charge windup (duration 40 ticks). */
    public static final int PHASE_TIME_STOP = 10;
    public static final int PHASE_TIME_STOP_CHARGE = 10;

    /** Normal punch attack (light or heavy based on {@link #heavyAttack}). */
    public static final int ABILITY_NORMAL = 0;
    /** Uppercut ability: uses the uppercut animation instead of heavy/light punch across phases 1..3. */
    public static final int ABILITY_UPPERCUT = 1;

    public static final float STAR_FINGER_MAX_REACH = 3.6F;
    public static final float STAR_FINGER_EXTEND_SPEED = 0.3F;
    public static final float STAR_FINGER_RETRACT_SPEED = 0.9F;
    public static final int STAR_FINGER_EXTEND_TICKS = 12;
    public static final int STAR_FINGER_HOLD_TICKS = 4;
    public static final int STAR_FINGER_RETRACT_TICKS = 4;
    public static final int STAR_FINGER_TOTAL_TICKS = 20;

    /**
     * 0 idle, 1 windup, 2 perform, 3 recovery (punches, see {@link #heavyAttack} and {@link #abilityKind}),
     * 4 barrage, 5 guard, 6 guard broken, 7 star finger, 8 inhale, 9 zoom, 10 time stop charge.
     * Other values are drawn as idle.
     */
    public int actionPhase;
    /**
     * Ability kind: 0 normal punch, 1 uppercut.
     * When 1, phases 1..3 use {@link StarPlatinumAnimData#UPPERCUT}.
     */
    public int abilityKind;
    /** {@code true} for the heavy punch pose, {@code false} for the light punch pose. Only used by phases 1..3 when abilityKind is normal. */
    public boolean heavyAttack;
    /** Ticks elapsed in the current action phase (barrage: since the barrage began), including partial tick. */
    public float actionTicks;
    /** Total length of the current action phase in ticks. */
    public float actionDuration;

    /** Local camera obstruction fade; does not change entity invisibility or another player's Stand. */
    public float cameraOpacity = 1;

    public int cameraTint() { return (Math.round(cameraOpacity * 255) << 24) | 0xFFFFFF; }

    /**
     * Calculates the extending finger reach in blocks based on the server authority formula:
     * max 3.6 units; 0.3/tick extends 12 ticks, hold 4 ticks, 0.9/tick retracts 4 ticks.
     */
    public float getStarFingerReach() {
        if (actionPhase != PHASE_STAR_FINGER) return 0.0F;
        float ticks = Math.clamp(actionTicks, 0.0F, 20.0F);
        if (ticks <= 12.0F) {
            return ticks * 0.3F;
        } else if (ticks <= 16.0F) {
            return 3.6F;
        } else {
            return Math.max(0.0F, 3.6F - (ticks - 16.0F) * 0.9F);
        }
    }
}
