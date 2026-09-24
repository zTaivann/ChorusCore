package dev.chorus.core;

import java.util.List;

/**
 * A self-contained feature of the core. Modules are started in registration order and
 * stopped in reverse, so a module may depend on anything installed before it.
 */
public interface ChorusModule {

    String name();

    /** The file under plugins/ChorusCore/ that holds this module's settings. */
    String configPath();

    /**
     * Every command this module owns, whether or not it ends up starting. The core needs
     * the list before {@link #enable()} runs so it can answer for them when the module is
     * switched off in its config.
     */
    List<String> commandNames();

    void enable();

    default void disable() {
    }

    default void reload() {
    }
}
