package dev.chorus.core;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * What the console sees when the plugin starts.
 *
 * <p>Sent as components rather than through the logger, which would put a level and a plugin
 * name in front of every line. Paper colours a component on its way to the console.
 */
final class Console {

    /** Deep to light, one shade a row, which reads as a single letterform rather than stripes. */
    private static final String[] SHADES = {
            "#5b2ea8", "#6f3ec4", "#8451da", "#9a68e8", "#b083f0", "#c6a3f7"};

    private static final String[] CHORUS = {
            "░█████╗░██╗░░██╗░█████╗░██████╗░██╗░░░██╗░██████╗",
            "██╔══██╗██║░░██║██╔══██╗██╔══██╗██║░░░██║██╔════╝",
            "██║░░╚═╝███████║██║░░██║██████╔╝██║░░░██║╚█████╗░",
            "██║░░██╗██╔══██║██║░░██║██╔══██╗██║░░░██║░╚═══██╗",
            "╚█████╔╝██║░░██║╚█████╔╝██║░░██║╚██████╔╝██████╔╝",
            "░╚════╝░╚═╝░░╚═╝░╚════╝░╚═╝░░╚═╝░╚═════╝░╚═════╝░"};

    private static final String[] CORE = {
            "░█████╗░░█████╗░██████╗░███████╗",
            "██╔══██╗██╔══██╗██╔══██╗██╔════╝",
            "██║░░╚═╝██║░░██║██████╔╝█████╗░░",
            "██║░░██╗██║░░██║██╔══██╗██╔══╝░░",
            "╚█████╔╝╚█████╔╝██║░░██║███████╗",
            "░╚════╝░░╚════╝░╚═╝░░╚═╝╚══════╝"};

    private static final String CONNECTED = "<color:#4caf50>✔</color> ";
    private static final String ABSENT = "<color:#6f6f6f>▪</color> ";
    private static final String LABEL = "<color:#9a68e8>";
    private static final String VALUE = "<color:#d7d7d7>";

    /** Long enough for the longest label, so the values line up in a column. */
    private static final int COLUMN = 15;

    private final ConsoleCommandSender console;

    Console(Plugin plugin) {
        this.console = plugin.getServer().getConsoleSender();
    }

    /** The letters themselves, for the check that keeps them rectangular. */
    static List<String[]> words() {
        return List.of(CHORUS, CORE);
    }

    void banner() {
        console.sendMessage(Component.empty());
        for (String[] word : List.of(CHORUS, CORE)) {
            for (int row = 0; row < word.length; row++) {
                line("  <color:" + SHADES[row] + ">" + word[row]);
            }
        }
        console.sendMessage(Component.empty());
    }

    /** The headline under the letters: what this is and what it is running on. */
    void title(String version, String server) {
        line("  <gradient:#a06bff:#e0c3fc>ChorusCore " + version + "</gradient>"
                + "  <color:#4a4a4a>·  " + VALUE + server);
        console.sendMessage(Component.empty());
    }

    /** Something the plugin found and is now using. Green, because it is good news. */
    void connected(String what, String detail) {
        line("  " + CONNECTED + LABEL + pad(what) + VALUE + detail);
    }

    /** Something optional that is not there. Not a warning: plenty of servers want it this way. */
    void absent(String what, String detail) {
        line("  " + ABSENT + "<color:#6f6f6f>" + pad(what) + detail);
    }

    void ready(long millis, String author) {
        console.sendMessage(Component.empty());
        line("  <color:#4caf50>Ready<white> in <color:#4caf50>" + millis + "ms");
        console.sendMessage(Component.empty());
        line("  " + VALUE + "Thank you for using this plugin!");
        line("  <color:#9a68e8>* " + author);
        console.sendMessage(Component.empty());
    }

    private void line(String written) {
        console.sendMessage(TextFormat.parse(written));
    }

    private static String pad(String label) {
        return label.length() >= COLUMN ? label + " " : label + " ".repeat(COLUMN - label.length());
    }
}
