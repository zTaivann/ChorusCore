package dev.chorus.core.locale;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a named colour turns into, which is the one thing the palette does. */
class PaletteTest {

    @Test
    void aNameBecomesWhatItStandsFor() throws InvalidConfigurationException {
        Palette palette = read("accent: '<color:#c9a227>'");

        assertEquals("<color:#c9a227>Hello", palette.apply("<c:accent>Hello"));
    }

    @Test
    void aCloseClosesWhatTheNameOpened() throws InvalidConfigurationException {
        Palette palette = read("title: '<gradient:#111111:#222222>'");

        assertEquals("<gradient:#111111:#222222>Homes</gradient>",
                palette.apply("<c:title>Homes</c>"));
    }

    /** One name may open two tags, and then closing it has to close both, in reverse. */
    @Test
    void aNameThatOpensTwoTagsClosesBoth() throws InvalidConfigurationException {
        Palette palette = read("shout: '<red><bold>'");

        assertEquals("<red><bold>Stop</bold></red>", palette.apply("<c:shout>Stop</c>"));
    }

    @Test
    void closesAreMatchedToTheirOwnOpening() throws InvalidConfigurationException {
        Palette palette = read("dim: '<gray>'\ntitle: '<gradient:#111111:#222222>'");

        assertEquals("<gray>» <gradient:#111111:#222222>Homes</gradient> «",
                palette.apply("<c:dim>» <c:title>Homes</c> «"));
    }

    /** A name may be written in terms of another, which is resolved once when it is read. */
    @Test
    void aNameMayStandForAnother() throws InvalidConfigurationException {
        Palette palette = read("bad: '<color:#b03a3a>'\nshout: '<c:bad><bold>'");

        assertEquals("<color:#b03a3a><bold>No", palette.apply("<c:shout>No"));
    }

    /** An undefined name is left alone, so MiniMessage's own {@code <c:red>} still works. */
    @Test
    void aNameThePaletteDoesNotKnowIsLeftAlone() throws InvalidConfigurationException {
        Palette palette = read("accent: '<color:#c9a227>'");

        assertEquals("<c:red>Hello", palette.apply("<c:red>Hello"));
    }

    @Test
    void aCloseWithNothingOpenIsLeftAlone() throws InvalidConfigurationException {
        Palette palette = read("accent: '<color:#c9a227>'");

        assertEquals("Hello</c>", palette.apply("Hello</c>"));
    }

    @Test
    void aLineThatNamesNoColourComesBackUntouched() throws InvalidConfigurationException {
        Palette palette = read("accent: '<color:#c9a227>'");
        String line = "<gray>Nothing to see here";

        assertEquals(line, palette.apply(line));
    }

    /** A name that stands for itself would be followed for ever, so it is reported instead. */
    @Test
    void aLoopIsReportedRatherThanFollowed() throws InvalidConfigurationException {
        List<String> reported = new ArrayList<>();
        Palette palette = Palette.read(yaml("one: '<c:two>'\ntwo: '<c:one>'"), reported::add);

        assertFalse(reported.isEmpty(), "the loop should have been reported");
        assertTrue(palette.apply("<c:one>x").endsWith("x"), "it should still read the line");
    }

    @Test
    void anEarlierNameStillWorks() throws InvalidConfigurationException {
        Palette palette = read("menu-homes: '<red>'");

        assertEquals("<red>Homes</red>", palette.apply("<c:screen-homes>Homes</c>"));
    }

    @Test
    void aColourSetUnderAnEarlierNameIsKept() throws InvalidConfigurationException {
        YamlConfiguration file = yaml("screen-homes: '<blue>'");
        file.setDefaults(yaml("menu-homes: '<red>'"));
        Palette palette = Palette.read(file, loop -> { });

        assertEquals("<blue>Homes</blue>", palette.apply("<c:menu-homes>Homes</c>"));
    }

    @Test
    void aNameTheFileLeavesOutComesFromTheBundledPalette() throws InvalidConfigurationException {
        YamlConfiguration file = yaml("accent: '<gold>'");
        file.setDefaults(yaml("accent: '<red>'\ngood: '<green>'"));
        Palette palette = Palette.read(file, loop -> { });

        assertEquals("<gold>a</gold>", palette.apply("<c:accent>a</c>"));
        assertEquals("<green>b</green>", palette.apply("<c:good>b</c>"));
    }

    private static Palette read(String lines) throws InvalidConfigurationException {
        return Palette.read(yaml(lines), loop -> { });
    }

    private static YamlConfiguration yaml(String lines) throws InvalidConfigurationException {
        YamlConfiguration data = new YamlConfiguration();
        data.loadFromString(lines);
        return data;
    }
}
