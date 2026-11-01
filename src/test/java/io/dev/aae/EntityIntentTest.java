package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityIntentTest {

    private static final long ENTITY = 1;
    private static final GridPoint CENTER = new GridPoint(2, 3);

    @Test
    void grabIsIncompatibleWithTranslationAndRotation() {
        EntityIntent grab = EntityIntent.grab(ENTITY, "a");

        assertFalse(grab.compatibleWith(EntityIntent.move(ENTITY, "b", 1, 0)));
        assertFalse(grab.compatibleWith(EntityIntent.move(ENTITY, "b", 0, -1)));
        assertFalse(grab.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 1)));
        assertFalse(grab.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, -1)));
    }

    @Test
    void releaseIsCompatibleWithTranslationAndRotation() {
        EntityIntent release = EntityIntent.release(ENTITY, "a");

        assertTrue(release.compatibleWith(EntityIntent.move(ENTITY, "b", 3, -2)));
        assertTrue(release.compatibleWith(EntityIntent.move(ENTITY, "b", 0, 0)));
        assertTrue(release.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 1)));
        assertTrue(release.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, -1)));
        assertTrue(release.compatibleWith(EntityIntent.release(ENTITY, "b")));
        assertTrue(release.compatibleWith(EntityIntent.grab(ENTITY, "b")));
    }

    @Test
    void multipleGrabsAndGrabReleasePairsNetOut() {
        EntityIntent grab = EntityIntent.grab(ENTITY, "a");

        assertTrue(grab.compatibleWith(EntityIntent.grab(ENTITY, "b")));
        assertTrue(grab.compatibleWith(EntityIntent.release(ENTITY, "b")));
    }

    @Test
    void stationaryHoldersAllowGrabAndReleaseButRejectMotion() {
        EntityIntent stay = EntityIntent.stay(ENTITY, "a");

        assertTrue(stay.compatibleWith(EntityIntent.stay(ENTITY, "b")));
        assertTrue(stay.compatibleWith(EntityIntent.grab(ENTITY, "b")));
        assertTrue(stay.compatibleWith(EntityIntent.release(ENTITY, "b")));
        assertFalse(stay.compatibleWith(EntityIntent.move(ENTITY, "b", 1, 0)));
        assertFalse(stay.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 1)));
        assertFalse(EntityIntent.move(ENTITY, "b", 1, 0).compatibleWith(stay));
        assertFalse(EntityIntent.rotate(ENTITY, "b", CENTER, 1).compatibleWith(stay));
    }

    @Test
    void identicalMotionsAreCompatibleAndDifferentMotionsAreNot() {
        EntityIntent east = EntityIntent.move(ENTITY, "a", 1, 0);

        assertTrue(east.compatibleWith(EntityIntent.move(ENTITY, "b", 1, 0)));
        assertFalse(east.compatibleWith(EntityIntent.move(ENTITY, "b", -1, 0)));
        assertFalse(east.compatibleWith(EntityIntent.move(ENTITY, "b", 0, 1)));
        assertFalse(east.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 1)));

        EntityIntent turn = EntityIntent.rotate(ENTITY, "a", CENTER, 1);
        assertTrue(turn.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 1)));
        assertFalse(turn.compatibleWith(EntityIntent.rotate(ENTITY, "b", CENTER, 3)));
        assertFalse(turn.compatibleWith(EntityIntent.rotate(ENTITY, "b", new GridPoint(3, 2), 1)));
    }

    @Test
    void rotationQuarterTurnsAreNormalizedAndMustNotBeZero() {
        assertEquals(3, EntityIntent.rotate(ENTITY, "a", CENTER, -1).quarterTurns());
        assertEquals(3, EntityIntent.rotate(ENTITY, "a", CENTER, 7).quarterTurns());
        assertEquals(1, EntityIntent.rotate(ENTITY, "a", CENTER, 5).quarterTurns());
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityIntent.rotate(ENTITY, "a", CENTER, 0));
    }

    @Test
    void factoriesRejectMixedPayloadsAndEntityIdsMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityIntent(
                        EntityIntent.Kind.MOVE, ENTITY, "a", 1, 0, CENTER, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityIntent(
                        EntityIntent.Kind.GRAB, ENTITY, "a", 1, 0, null, 0));
        assertThrows(
                NullPointerException.class,
                () -> new EntityIntent(
                        EntityIntent.Kind.ROTATE, ENTITY, "a", 0, 0, null, 1));
    }
}
