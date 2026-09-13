package dev.chorus.core.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTextTest {

    @Test
    void everythingBeforeTheFirstChapterIsTheOpeningOne() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of(
                "Welcome",
                "to the server",
                "#rules",
                "Be nice"));

        assertEquals(2, chapters.size());
        assertEquals("", chapters.get(0).name());
        assertEquals(List.of("Welcome", "to the server"), chapters.get(0).lines());
        assertEquals("rules", chapters.get(1).name());
    }

    /** The space is the whole of the difference, so it is worth pinning down. */
    @Test
    void aHashWithASpaceIsANoteAndNeverAChapter() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of(
                "# Edit this file to change the welcome text.",
                "#",
                "Welcome"));

        assertEquals(1, chapters.size());
        assertEquals("", chapters.get(0).name());
        assertEquals(List.of("Welcome"), chapters.get(0).lines(),
                "the notes should not have reached anybody");
    }

    @Test
    void aSecondWordOnTheChapterLineIsItsPermission() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of(
                "#staff chorus.admin",
                "Only for staff"));

        assertEquals(1, chapters.size());
        assertEquals("staff", chapters.get(0).name());
        assertEquals("chorus.admin", chapters.get(0).permission());
    }

    @Test
    void aChapterWithNoPermissionIsOpen() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of("#rules", "Be nice"));
        assertEquals("", chapters.get(0).permission());
    }

    @Test
    void aChapterNameIsReadTheSameWhateverTheCase() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of("#Rules", "Be nice"));
        assertEquals("rules", chapters.get(0).name());
    }

    @Test
    void theBlankLinesUnderAChapterAreLeftOff() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of(
                "#rules",
                "Be nice",
                "",
                "",
                "#commands",
                "/home",
                ""));

        assertEquals(List.of("Be nice"), chapters.get(0).lines());
        assertEquals(List.of("/home"), chapters.get(1).lines());
    }

    /** A blank line in the middle is spacing the writer asked for. */
    @Test
    void aBlankLineInTheMiddleIsKept() {
        List<ServerText.Chapter> chapters = ServerText.parse(List.of(
                "Welcome",
                "",
                "Have fun"));

        assertEquals(List.of("Welcome", "", "Have fun"), chapters.get(0).lines());
    }

    @Test
    void aFileOfNothingButNotesHoldsNoChapters() {
        assertTrue(ServerText.parse(List.of("# a note", "# another")).isEmpty());
    }

    @Test
    void aPageIsNineLinesAndTheRestGoOnTheNext() {
        List<String> lines = new ArrayList<>();
        for (int line = 1; line <= 10; line++) {
            lines.add("line " + line);
        }
        ServerText.Chapter chapter = ServerText.parse(lines).get(0);

        assertEquals(2, ServerText.pages(chapter));
    }
}
