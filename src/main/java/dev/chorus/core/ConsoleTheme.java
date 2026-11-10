package dev.chorus.core;

import dev.chorus.commons.Console;

/** How this plugin's startup banner looks; the drawing itself is shared. */
final class ConsoleTheme {

    static final Console.Theme CORE = new Console.Theme("ChorusCore", new String[] {
            "░█████╗░░█████╗░██████╗░███████╗",
            "██╔══██╗██╔══██╗██╔══██╗██╔════╝",
            "██║░░╚═╝██║░░██║██████╔╝█████╗░░",
            "██║░░██╗██║░░██║██╔══██╗██╔══╝░░",
            "╚█████╔╝╚█████╔╝██║░░██║███████╗",
            "░╚════╝░░╚════╝░╚═╝░░╚═╝╚══════╝"},
            new String[] {"#5b2ea8", "#6f3ec4", "#8451da", "#9a68e8", "#b083f0", "#c6a3f7"},
            "#4caf50", "#9a68e8", "#a06bff:#e0c3fc");

    private ConsoleTheme() {
    }
}
