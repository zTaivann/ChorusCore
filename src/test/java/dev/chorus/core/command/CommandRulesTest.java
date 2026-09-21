package dev.chorus.core.command;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a command block inherits from the defaults and what it writes down for itself. */
class CommandRulesTest {

    private static final Logger QUIET = Logger.getLogger("CommandRulesTest");

    @Test
    void aCommandWithNoLinesOfItsOwnHasNone() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  cooldown-seconds: 5
                fly:
                  enabled: true
                """, "fly");

        assertEquals(Map.of(), rules.messages());
        assertEquals(5, rules.cooldownSeconds());
    }

    @Test
    void aLineIsReadByTheKeyTheMessagesFolderUses() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  enabled: true
                fly:
                  messages:
                    error.no-permission: '<red>Buy VIP.'
                """, "fly");

        assertEquals("<red>Buy VIP.", rules.messages().get("error.no-permission"));
    }

    /** Written as nested blocks, which is the same key and the same result. */
    @Test
    void theKeyMayBeWrittenAsBlocksInstead() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  enabled: true
                fly:
                  messages:
                    error:
                      no-permission: '<red>Buy VIP.'
                """, "fly");

        assertEquals("<red>Buy VIP.", rules.messages().get("error.no-permission"));
    }

    /** A blank line is a command silencing that message, so it has to reach the view as one. */
    @Test
    void aBlankLineIsKeptRatherThanDropped() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  enabled: true
                fly:
                  messages:
                    cooldown.wait: ''
                """, "fly");

        assertTrue(rules.messages().containsKey("cooldown.wait"));
        assertEquals("", rules.messages().get("cooldown.wait"));
    }

    /** The defaults block may set a line for every command in the file, one at a time. */
    @Test
    void aCommandAddsToTheDefaultLinesRatherThanReplacingThem()
            throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  messages:
                    error.command-disabled: '<red>Not here.'
                fly:
                  messages:
                    error.no-permission: '<red>Buy VIP.'
                """, "fly");

        assertEquals(2, rules.messages().size());
        assertEquals("<red>Not here.", rules.messages().get("error.command-disabled"));
        assertEquals("<red>Buy VIP.", rules.messages().get("error.no-permission"));
    }

    @Test
    void aCommandMayRewriteALineTheDefaultsSet() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  messages:
                    error.no-permission: '<red>No.'
                fly:
                  messages:
                    error.no-permission: '<red>Buy VIP.'
                """, "fly");

        assertEquals("<red>Buy VIP.", rules.messages().get("error.no-permission"));
    }

    private static CommandRules read(String yaml, String command)
            throws InvalidConfigurationException {
        YamlConfiguration commands = new YamlConfiguration();
        commands.loadFromString(yaml);
        return CommandRules.read(commands, command, QUIET);
    }
}
