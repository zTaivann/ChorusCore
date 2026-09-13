package dev.chorus.core.kits;

import dev.chorus.core.kits.KitEditor.Rule;
import dev.chorus.core.kits.rules.KitAction;
import dev.chorus.core.kits.rules.Requirement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim actions, the fail actions and the requirements, through the file and back.
 *
 * <p>The screen that edits them works by position, so a line that changes shape on the way
 * to disk and back is a line that gets rewritten in the wrong place. That is the bug this
 * is here to catch, and it is the same one that once made a saved kit icon come back as a
 * chest.
 */
class KitRulesTest {

    @Test
    void aPlainLineSurvivesTheFile() throws InvalidConfigurationException {
        List<Rule> written = List.of(Rule.of("message: <green>Enjoy"), Rule.of("close"));
        List<Rule> back = throughTheFile(written);

        assertEquals(written, back);
    }

    /** A requirement with its own refusal is a block, and the block has to come back whole. */
    @Test
    void aRefusalLineSurvivesTheFile() throws InvalidConfigurationException {
        List<Rule> written = List.of(
                Rule.of("permission: chorus.kit.vip"),
                new Rule("placeholder: %player_level% >= 10", "<red>Come back at level 10."));

        assertEquals(written, throughTheFile(written));
    }

    /** Blank is not a refusal line, and writing it as one would leave a half-empty block. */
    @Test
    void anEmptyRefusalIsWrittenAsABareLine() {
        assertEquals(List.of("money: 500"),
                KitEditor.describe(List.of(new Rule("money: 500", ""))));
        assertEquals(List.of("money: 500"),
                KitEditor.describe(List.of(new Rule("money: 500", null))));
    }

    /**
     * An emptied list stays empty.
     *
     * <p>The copy of the config bundled in the jar backs the one on disk, so a list that is
     * taken out altogether goes back to whatever the example kits ship with. Somebody
     * removing the last action from the daily kit would find it there again after a reload.
     */
    @Test
    void anEmptiedListIsStillAList() throws InvalidConfigurationException {
        YamlConfiguration file = new YamlConfiguration();
        file.set("kits.definitions.daily.claim-actions", KitEditor.describe(List.of()));

        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(file.saveToString());

        Object stored = reloaded.get("kits.definitions.daily.claim-actions", null);
        assertNotNull(stored, "the list has to still be there, or the bundled copy shows through");
        assertEquals(List.of(), KitEditor.read(stored));
    }

    @Test
    void nothingWrittenReadsAsNothing() {
        assertEquals(List.of(), KitEditor.read(null));
        assertEquals(List.of(), KitEditor.read("not a list"));
    }

    /** Every line the screen can write is one the plugin can read back as a rule. */
    @Test
    void whatTheScreenWritesIsWhatTheKitReads() throws InvalidConfigurationException {
        List<Rule> actions = throughTheFile(List.of(
                Rule.of("message: <green>Enjoy"),
                Rule.of("sound: entity.player.levelup 1 1.4"),
                Rule.of("console: lp user %player% parent add vip")));

        for (Rule rule : actions) {
            assertNotNull(KitAction.of(rule.line()), rule.line() + " should be an action");
        }

        List<Rule> requirements = throughTheFile(List.of(
                Rule.of("permission: chorus.kit.vip"),
                new Rule("money: 500", "<red>Not enough.")));

        for (Rule rule : requirements) {
            assertNotNull(Requirement.of(rule.line(), rule.deny()),
                    rule.line() + " should be a requirement");
        }
    }

    /** A line nobody understands is still kept, so the screen can show it and fix it. */
    @Test
    void aLineTheParserRejectsIsStillHeld() throws InvalidConfigurationException {
        List<Rule> back = throughTheFile(List.of(Rule.of("mesage: typo")));

        assertEquals(1, back.size());
        assertEquals("mesage: typo", back.get(0).line());
        assertNull(KitAction.of(back.get(0).line()), "the parser should still turn it down");
    }

    @Test
    void positionsAreTheFilesOwn() throws InvalidConfigurationException {
        List<Rule> back = throughTheFile(List.of(
                Rule.of("mesage: typo"),
                Rule.of("close")));

        assertTrue(back.size() == 2 && "close".equals(back.get(1).line()),
                "a line the parser drops must not shift the ones after it");
    }

    /** Written, saved as text, loaded again: the shape the editor actually meets. */
    private static List<Rule> throughTheFile(List<Rule> rules) throws InvalidConfigurationException {
        YamlConfiguration file = new YamlConfiguration();
        file.set("kits.definitions.vip.requirements", KitEditor.describe(rules));

        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(file.saveToString());

        return KitEditor.read(reloaded.get("kits.definitions.vip.requirements", null));
    }
}
