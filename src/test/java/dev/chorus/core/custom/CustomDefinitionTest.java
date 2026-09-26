package dev.chorus.core.custom;

import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A command made in the config takes the same conditions and actions as the plugin's own. */
class CustomDefinitionTest {

    @Test
    void theNewOptionsAreRead() throws InvalidConfigurationException {
        CustomDefinition vote = read("""
                permission: vote.use
                permission-message: '<red>Vote on the website first.'
                messages: [ 'Thanks!' ]
                requires:
                  - 'playtime: 3600'
                on-success:
                  - 'title: <gold>Thanks;<gray>for voting'
                on-fail:
                  - 'sound: entity.villager.no'
                log: true
                """);

        assertEquals("<red>Vote on the website first.", vote.permissionMessage());
        assertEquals(List.of(Requirement.Kind.PLAYTIME),
                vote.requires().stream().map(Requirement::kind).toList());
        assertEquals(List.of(Action.Kind.TITLE),
                vote.onSuccess().stream().map(Action::kind).toList());
        assertEquals(1, vote.onFail().size());
        assertTrue(vote.log());
    }

    @Test
    void leftOutTheyChangeNothing() throws InvalidConfigurationException {
        CustomDefinition plain = read("""
                messages: [ 'Hello' ]
                """);

        assertEquals("", plain.permissionMessage());
        assertTrue(plain.requires().isEmpty());
        assertTrue(plain.onSuccess().isEmpty());
        assertFalse(plain.log());
    }

    /** A command whose only effect is an action still does something. */
    @Test
    void actionsAloneAreEnough() throws InvalidConfigurationException {
        assertFalse(read("on-success: [ 'actionbar: Hi' ]").doesNothing());
        assertTrue(read("log: true").doesNothing());
    }

    @Test
    void aLineThePluginDoesNotKnowIsNamed() throws InvalidConfigurationException {
        List<String> problems = new ArrayList<>();
        YamlConfiguration block = new YamlConfiguration();
        block.loadFromString("on-fail: [ 'shout: hi' ]\nrequires: [ 'level: 5' ]");

        CustomDefinition.read(block, "vote", problems::add);

        assertEquals(List.of(
                "/vote has a requirement this plugin does not know: 'level: 5'",
                "/vote has an action this plugin does not know: 'shout: hi'"), problems);
    }

    private static CustomDefinition read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration block = new YamlConfiguration();
        block.loadFromString(yaml);
        return CustomDefinition.read(block, "vote", problem -> { });
    }
}
