package dev.chorus.core.command;

import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void aCommandMayTakeAnotherPermission() throws InvalidConfigurationException {
        String yaml = """
                defaults:
                  enabled: true
                fly:
                  permission: vip.fly
                open:
                  permission: ''
                """;

        assertEquals("vip.fly", read(yaml, "fly").permission());
        assertEquals("", read(yaml, "open").permission());
        assertNull(read(yaml, "heal").permission(), "without one it keeps its own node");
    }

    /** One node for every command in a file would be a mistake nobody means to make. */
    @Test
    void aPermissionInTheDefaultsIsIgnored() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  permission: ''
                fly:
                  enabled: true
                """, "fly");

        assertNull(rules.permission());
    }

    @Test
    void thePermissionMessageIsTheNoPermissionLine() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  permission-message: ''
                fly:
                  permission-message: '<red>Buy VIP.'
                """, "fly");

        assertEquals("<red>Buy VIP.", rules.messages().get("error.no-permission"));
        assertEquals(Map.of(), read("""
                defaults:
                  permission-message: ''
                """, "fly").messages(), "an empty one keeps the usual line");
    }

    @Test
    void requirementsActionsAndTheLogAreRead() throws InvalidConfigurationException {
        CommandRules rules = read("""
                defaults:
                  log: false
                fly:
                  requires:
                    - 'money: 500'
                    - condition: 'placeholder: %player_level% >= 10'
                      deny: '<red>Level 10 first.'
                  on-success:
                    - 'actionbar: <green>Flying'
                  on-fail:
                    - 'sound: entity.villager.no'
                    - 'message: <red>No.'
                  log: true
                """, "fly");

        assertEquals(List.of(Requirement.Kind.MONEY, Requirement.Kind.PLACEHOLDER),
                rules.requires().stream().map(Requirement::kind).toList());
        assertEquals("<red>Level 10 first.", rules.requires().get(1).denyMessage());
        assertEquals(List.of(Action.Kind.ACTIONBAR),
                rules.onSuccess().stream().map(Action::kind).toList());
        assertEquals(2, rules.onFail().size());
        assertTrue(rules.log());
    }

    /** The command's own list stands instead of the defaults', rather than adding to it. */
    @Test
    void aCommandsOwnListReplacesTheDefaults() throws InvalidConfigurationException {
        String yaml = """
                defaults:
                  requires: [ 'money: 5' ]
                  on-success: [ 'actionbar: Done' ]
                fly:
                  requires: [ 'permission: vip.fly' ]
                """;

        CommandRules fly = read(yaml, "fly");
        assertEquals(List.of(Requirement.Kind.PERMISSION),
                fly.requires().stream().map(Requirement::kind).toList());
        assertEquals(1, fly.onSuccess().size(), "what it does not write comes from the defaults");
        assertEquals(List.of(Requirement.Kind.MONEY),
                read(yaml, "heal").requires().stream().map(Requirement::kind).toList());
    }

    @Test
    void aLineThePluginDoesNotKnowIsSkipped() throws InvalidConfigurationException {
        CommandRules rules = read("""
                fly:
                  requires: [ 'mony: 5', 'money: 5' ]
                  on-success: [ 'mesage: hi', 'message: hi' ]
                """, "fly");

        assertEquals(1, rules.requires().size());
        assertEquals(1, rules.onSuccess().size());
    }

    @Test
    void aCooldownGroupIsReadInLowerCaseAndInherited() throws InvalidConfigurationException {
        String yaml = """
                defaults:
                  cooldown-group: Travel
                home:
                  enabled: true
                heal:
                  cooldown-group: ''
                """;

        assertEquals("travel", read(yaml, "home").cooldownGroup());
        assertEquals("", read(yaml, "heal").cooldownGroup(), "empty keeps its own wait");
        assertEquals("", read("fly:\n  enabled: true\n", "fly").cooldownGroup());
    }

    @Test
    void aHomeOrAWarpKeepsEverythingButWhatItCosts() throws InvalidConfigurationException {
        CommandRules rules = read("""
                fly:
                  price: 10
                  cooldown-seconds: 5
                  cooldown-group: travel
                  log: true
                  on-success: [ 'actionbar: Done' ]
                """, "fly").costing(99, 60);

        assertEquals("travel", rules.cooldownGroup());
        assertEquals(99, rules.price());
        assertEquals(60, rules.cooldownSeconds());
        assertTrue(rules.log());
        assertEquals(1, rules.onSuccess().size());
    }

    private static CommandRules read(String yaml, String command)
            throws InvalidConfigurationException {
        YamlConfiguration commands = new YamlConfiguration();
        commands.loadFromString(yaml);
        return CommandRules.read(commands, command, QUIET);
    }
}
