package dev.chorus.core.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One timer per command, or one for every command in a group. */
class CooldownsTest {

    private static final long NOW = 1_000_000;

    private final Cooldowns cooldowns = new Cooldowns();
    private final UUID player = UUID.randomUUID();

    @Test
    void aCommandWithNoGroupKeepsItsOwnTimer() {
        cooldowns.start(player, Cooldowns.timer("home", ""), 30, NOW);

        assertTrue(cooldowns.remaining(player, Cooldowns.timer("home", ""), NOW) > 0);
        assertEquals(0, cooldowns.remaining(player, Cooldowns.timer("warp", ""), NOW));
    }

    @Test
    void usingOneCommandInAGroupHoldsBackTheOthers() {
        cooldowns.start(player, Cooldowns.timer("home", "travel"), 30, NOW);

        assertEquals(30_000, cooldowns.remaining(player, Cooldowns.timer("warp", "travel"), NOW));
        assertEquals(30_000, cooldowns.remaining(player, Cooldowns.timer("custom:vote", "travel"), NOW));
    }

    /** The wait is as long as the command that started it said, whoever asks. */
    @Test
    void theCommandUsedSetsHowLongTheGroupWaits() {
        cooldowns.start(player, Cooldowns.timer("warp", "travel"), 5, NOW);

        assertEquals(1_000, cooldowns.remaining(player, Cooldowns.timer("home", "travel"), NOW + 4_000));
        assertEquals(0, cooldowns.remaining(player, Cooldowns.timer("home", "travel"), NOW + 5_000));
    }

    @Test
    void twoGroupsAreTwoTimers() {
        cooldowns.start(player, Cooldowns.timer("home", "travel"), 30, NOW);

        assertEquals(0, cooldowns.remaining(player, Cooldowns.timer("heal", "healing"), NOW));
    }

    @Test
    void aGroupCannotBeMistakenForACommand() {
        cooldowns.start(player, Cooldowns.timer("home", "warp"), 30, NOW);

        assertEquals(0, cooldowns.remaining(player, Cooldowns.timer("warp", ""), NOW),
                "a group called warp is not the /warp command's own timer");
    }
}
