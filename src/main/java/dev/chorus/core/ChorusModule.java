package dev.chorus.core;

import java.util.List;

/** A feature of the core. Modules start in registration order and stop in reverse. */
public interface ChorusModule {

    String name();

    /** The file under plugins/ChorusCore/ that holds this module's settings. */
    String configPath();

    /** Every command this module owns, read before {@link #enable()}. */
    List<String> commandNames();

    void enable();

    default void disable() {
    }

    default void reload() {
    }
}
