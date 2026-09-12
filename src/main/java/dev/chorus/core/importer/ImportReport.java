package dev.chorus.core.importer;

import java.util.ArrayList;
import java.util.List;

/** What an import found, and what it would do or did. */
public final class ImportReport {

    private static final int MAX_PROBLEMS = 20;

    private final List<String> problems = new ArrayList<>();

    private int files;
    private int players;
    private int homes;
    private int balances;
    private int nicknames;
    private int mail;
    private int warps;
    private int worth;
    private int skipped;
    private int hidden;

    void readFile() {
        files++;
    }

    void player() {
        players++;
    }

    void home() {
        homes++;
    }

    void balance() {
        balances++;
    }

    void nickname() {
        nicknames++;
    }

    void letter() {
        mail++;
    }

    void warp() {
        warps++;
    }

    void price() {
        worth++;
    }

    /** Something already in Chorus that the import left exactly as it was. */
    void skip() {
        skipped++;
    }

    /**
     * A file that could not be read.
     *
     * <p>Only the first handful are kept. A folder of ten thousand broken files is one
     * problem, and printing ten thousand lines about it helps nobody.
     */
    void problem(String what) {
        if (problems.size() < MAX_PROBLEMS) {
            problems.add(what);
        } else {
            hidden++;
        }
    }

    public int files() {
        return files;
    }

    public int players() {
        return players;
    }

    public int homes() {
        return homes;
    }

    public int balances() {
        return balances;
    }

    public int nicknames() {
        return nicknames;
    }

    public int mail() {
        return mail;
    }

    public int warps() {
        return warps;
    }

    public int worth() {
        return worth;
    }

    public int skipped() {
        return skipped;
    }

    public int hiddenProblems() {
        return hidden;
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean foundAnything() {
        return files > 0 || warps > 0 || worth > 0;
    }
}
