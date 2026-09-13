package dev.chorus.core.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    @Test
    void theSameVersionIsNotNewer() {
        assertFalse(Versions.isNewer("1.2.3", "1.2.3"));
        assertEquals(0, Versions.compare("1.2.3", "1.2.3"));
    }

    /** The one a plain string comparison gets wrong. */
    @Test
    void tenComesAfterNine() {
        assertTrue(Versions.isNewer("0.10.0", "0.9.0"));
        assertFalse(Versions.isNewer("0.9.0", "0.10.0"));
    }

    @Test
    void aMissingPartCountsAsZero() {
        assertEquals(0, Versions.compare("1.2", "1.2.0"));
        assertTrue(Versions.isNewer("1.2.1", "1.2"));
    }

    @Test
    void aLeadingVIsIgnored() {
        assertEquals(0, Versions.compare("v1.2.0", "1.2.0"));
        assertTrue(Versions.isNewer("v1.3.0", "1.2.0"));
    }

    @Test
    void aPreReleaseComesBeforeTheReleaseItLeadsTo() {
        assertTrue(Versions.isNewer("1.2.0", "1.2.0-SNAPSHOT"));
        assertFalse(Versions.isNewer("1.2.0-rc1", "1.2.0"));
    }

    @Test
    void aPreReleaseOfALaterVersionIsStillLater() {
        assertTrue(Versions.isNewer("1.3.0-rc1", "1.2.0"));
    }

    @Test
    void nonsenseIsNeverNewerThanARealVersion() {
        assertFalse(Versions.isNewer("", "0.1.0"));
        assertFalse(Versions.isNewer("not a version", "0.1.0"));
    }
}
