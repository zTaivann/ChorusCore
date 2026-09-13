package dev.chorus.core.importer;

import java.util.ArrayList;
import java.util.List;

/** What a shop import found, and what it would do or did. */
public final class ShopImportReport {

    private static final int MAX_PROBLEMS = 10;

    private final List<String> problems = new ArrayList<>();

    private String source = "";
    private int read;
    private int imported;
    private int skipped;
    private int unreadable;
    private int hidden;

    void from(String kind) {
        this.source = kind;
    }

    void shop() {
        read++;
    }

    void wrote() {
        imported++;
    }

    /** A block Chorus already has a shop on, left exactly as it was. */
    void skip() {
        skipped++;
    }

    /** A row whose owner or item could not be read. Counted, never guessed at. */
    void broken() {
        unreadable++;
    }

    void problem(String what) {
        if (problems.size() < MAX_PROBLEMS) {
            problems.add(what);
        } else {
            hidden++;
        }
    }

    /** Which kind of database it came out of, for the line that reports it. */
    public String source() {
        return source;
    }

    public int read() {
        return read;
    }

    public int imported() {
        return imported;
    }

    public int skipped() {
        return skipped;
    }

    public int unreadable() {
        return unreadable;
    }

    public int hiddenProblems() {
        return hidden;
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean foundAnything() {
        return read > 0;
    }
}
