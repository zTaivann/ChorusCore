package dev.chorus.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRuleTest {

    @Test
    void anEmptyListIsEverywhere() {
        WorldRule rule = WorldRule.of(List.of());

        assertTrue(rule.allows("world"));
        assertTrue(rule.allows("anything_at_all"));
        assertFalse(rule.restricted());
    }

    @Test
    void aNameOnItsOwnIsTheOnlyPlaceItWorks() {
        WorldRule rule = WorldRule.of(List.of("world", "world_nether"));

        assertTrue(rule.allows("world"));
        assertTrue(rule.allows("world_nether"));
        assertFalse(rule.allows("event"));
        assertTrue(rule.restricted());
    }

    @Test
    void aNameWithAnExclamationIsTheOnlyPlaceItDoesNot() {
        WorldRule rule = WorldRule.of(List.of("!event"));

        assertFalse(rule.allows("event"));
        assertTrue(rule.allows("world"));
        assertTrue(rule.allows("world_the_end"));
    }

    /** A world folder and a name typed in a config rarely agree on the case. */
    @Test
    void theCaseDoesNotMatter() {
        assertTrue(WorldRule.of(List.of("World")).allows("world"));
        assertFalse(WorldRule.of(List.of("!Event")).allows("EVENT"));
    }

    @Test
    void aRefusalBeatsAnAllowanceForTheSameWorld() {
        WorldRule rule = WorldRule.of(List.of("event", "!event"));
        assertFalse(rule.allows("event"));
    }

    @Test
    void blankEntriesAreIgnoredRatherThanBanningEverything() {
        WorldRule rule = WorldRule.of(List.of("", "  "));
        assertTrue(rule.allows("world"));
    }
}
