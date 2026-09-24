package dev.chorus.core.teleport;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatesTest {

    @Test
    void numbersAndOffsetsAreCoordinates() {
        assertTrue(Coordinates.areNumbers(new String[]{"10", "~", "~-5"}, 0));
        assertFalse(Coordinates.areNumbers(new String[]{"Steve", "64", "0"}, 0));
    }

    @Test
    void nothingPastTheEdgeOfTheWorld() {
        assertFalse(Coordinates.areNumbers(new String[]{"1e300", "64", "0"}, 0));
        assertFalse(Coordinates.areNumbers(new String[]{"30000001", "64", "0"}, 0));
        assertFalse(Coordinates.areNumbers(new String[]{"NaN", "64", "0"}, 0));
        assertFalse(Coordinates.areNumbers(new String[]{"~1e300", "64", "0"}, 0));
    }

    @Test
    void anOffsetIsMeasuredFromWhereTheyStand() {
        Location origin = new Location(null, 10, 64, 3, 90f, 0f);
        Location found = Coordinates.read(new String[]{"~5", "~", "-2,5"}, 0, null, origin);

        assertNotNull(found);
        assertEquals(15, found.getX());
        assertEquals(64, found.getY());
        assertEquals(-2.5, found.getZ());
        assertEquals(90f, found.getYaw());
    }
}
