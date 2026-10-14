package com.github.standobyte.jojo.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbitRotationTest {
    @Test void orbitWrapsAndClampsWithoutTargetInput() {
        var orbit = new OrbitRotation();
        orbit.reset(179, 0);
        orbit.turn(20, 1000);
        assertEquals(-178, orbit.yaw(), 0.0001);
        assertEquals(90, orbit.pitch());
        orbit.turn(0, -2000);
        assertEquals(-90, orbit.pitch());
        orbit.turn(Double.NaN, 0);
        assertEquals(-178, orbit.yaw(), 0.0001);
    }

    @Test void allEightDirectionsFaceTravelWithoutTurningCamera() {
        for (int strafe = -1; strafe <= 1; strafe++) for (int forward = -1; forward <= 1; forward++) {
            if (strafe == 0 && forward == 0) continue;
            float yaw = OrbitRotation.facing(37, strafe, forward);
            float[] movement = OrbitRotation.movement(37, yaw, strafe, forward);
            assertEquals(0, movement[0], 0.00001);
            assertEquals(Math.hypot(strafe, forward), movement[1], 0.00001);
        }
    }

    @Test void aimingPreservesCameraRelativeStrafingAndSpeed() {
        for (float camera : new float[] {-179, -90, 0, 90, 179}) {
            float[] moved = OrbitRotation.movement(camera, 22, .6F, .8F);
            assertEquals(1, Math.hypot(moved[0], moved[1]), 0.00001);
            float[] restored = OrbitRotation.movement(22, camera, moved[0], moved[1]);
            assertArrayEquals(new float[] {.6F, .8F}, restored, 0.00001F);
        }
    }
}
