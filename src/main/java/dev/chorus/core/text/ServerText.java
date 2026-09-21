package dev.chorus.core.text;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** A text file in the plugin folder, read as chapters of pages. */
public final class ServerText {

    private static final int LINES_PER_PAGE = 9;

    private final Plugin plugin;
    private final String path;

    private volatile List<Chapter> chapters = List.of();

    public ServerText(Plugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
    }

    /** A named run of lines. The opening one has an empty name. */
    public record Chapter(String name, String permission, List<String> lines) {

        public boolean readableBy(CommandSender sender) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    }

    /** Re-reads the file, writing out the bundled copy first if there is none. */
    public void reload() {
        File onDisk = new File(plugin.getDataFolder(), path);
        if (!onDisk.exists()) {
            plugin.saveResource(path, false);
        }

        try {
            chapters = parse(Files.readAllLines(onDisk.toPath(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + path, exception);
            chapters = List.of();
        }
    }

    public boolean isEmpty() {
        return chapters.isEmpty();
    }

    /** The opening chapter, or null when the file holds nothing but named chapters. */
    public @Nullable Chapter opening() {
        for (Chapter chapter : chapters) {
            if (chapter.name().isEmpty()) {
                return chapter;
            }
        }
        return null;
    }

    public @Nullable Chapter chapter(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Chapter chapter : chapters) {
            if (chapter.name().equals(wanted)) {
                return chapter;
            }
        }
        return null;
    }

    /** The named chapters this sender is allowed to read, for the footer and tab completion. */
    public List<String> readableNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (!chapter.name().isEmpty() && chapter.readableBy(sender)) {
                names.add(chapter.name());
            }
        }
        return names;
    }

    public static int pages(Chapter chapter) {
        return Math.max(1, (chapter.lines().size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    /** One page of a chapter, already filled in for whoever is reading it. */
    public static List<String> page(Chapter chapter, int page, CommandSender reader) {
        int from = (page - 1) * LINES_PER_PAGE;
        if (from >= chapter.lines().size()) {
            return List.of();
        }
        int to = Math.min(chapter.lines().size(), from + LINES_PER_PAGE);

        List<String> filled = new ArrayList<>(to - from);
        for (String line : chapter.lines().subList(from, to)) {
            filled.add(fill(line, reader));
        }
        return filled;
    }

    private static String fill(String line, CommandSender reader) {
        String name = reader.getName();
        String world = reader instanceof Player player ? player.getWorld().getName() : "";
        return line.replace("%player%", name)
                .replace("%world%", world)
                .replace("%online%", String.valueOf(reader.getServer().getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(reader.getServer().getMaxPlayers()));
    }

    /** Package-private so the checks can read a file without standing up a server. */
    static List<Chapter> parse(List<String> lines) {
        List<Chapter> found = new ArrayList<>();
        String name = "";
        String permission = "";
        List<String> body = new ArrayList<>();

        for (String line : lines) {
            if (isComment(line)) {
                continue;
            }
            if (!isChapter(line)) {
                body.add(line);
                continue;
            }

            close(found, name, permission, body);
            body.clear();

            String[] parts = line.substring(1).trim().split("\\s+", 2);
            name = parts[0].toLowerCase(Locale.ROOT);
            permission = parts.length > 1 ? parts[1].trim() : "";
        }

        close(found, name, permission, body);
        return List.copyOf(found);
    }

    /** {@code #rules} opens a chapter. The space is what tells the two apart. */
    private static boolean isChapter(String line) {
        return line.length() > 1 && line.charAt(0) == '#' && !Character.isWhitespace(line.charAt(1));
    }

    /** {@code # anything} is a note to whoever is editing the file and is never shown. */
    private static boolean isComment(String line) {
        return line.startsWith("#") && !isChapter(line);
    }

    /** Ends the chapter being read, with the blank lines under it left off. */
    private static void close(List<Chapter> found, String name, String permission,
                              List<String> body) {
        int end = body.size();
        while (end > 0 && body.get(end - 1).isBlank()) {
            end--;
        }
        if (end == 0 && name.isEmpty()) {
            return;
        }
        found.add(new Chapter(name, permission, List.copyOf(body.subList(0, end))));
    }
}
