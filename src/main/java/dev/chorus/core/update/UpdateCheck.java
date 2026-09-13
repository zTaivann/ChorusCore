package dev.chorus.core.update;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks once a day whether a newer ChorusCore has been released.
 *
 * <p>It is a plain GET for a version number. Nothing is downloaded, nothing is installed and
 * nothing about this server is sent — not the address, not the player count, not the version
 * being run. What the site learns is that somebody asked, which is what any download link
 * learns anyway.
 *
 * <p>Everything about it fails quietly. A site that is down, a project that has been renamed
 * or a server with no way out to the internet all end the same way: no answer, no message, no
 * line in the log above {@code FINE}. An update notice is worth having and worth nothing at
 * all next to a console full of stack traces.
 */
public final class UpdateCheck implements Listener {

    private static final String PERMISSION = "chorus.updates";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final long FIRST_TICKS = 20L * 30;
    private static final long DAILY_TICKS = 20L * 60 * 60 * 24;

    /** The first "version_number" of a Modrinth answer, which lists the newest first. */
    private static final Pattern MODRINTH = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HANGAR = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PLAIN_VERSION = Pattern.compile("^[vV]?[0-9][0-9A-Za-z.\\-_+]{0,31}$");

    /** Where to ask. Each one answers with the newest version and nothing else worth reading. */
    public enum Source {
        MODRINTH, HANGAR, SPIGOT, PLAIN
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Schedulers schedulers;
    private final Executor worker;
    private final String running;

    private volatile boolean enabled;
    private volatile boolean notifyStaff = true;
    private volatile Source source = Source.MODRINTH;
    private volatile String project = "";
    private volatile String url = "";

    /** Empty until a check has found something later than what is running. */
    private volatile String newer = "";

    private ChorusTask task = ChorusTask.NONE;

    public UpdateCheck(Plugin plugin, Messages messages, Schedulers schedulers, Executor worker) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedulers = schedulers;
        this.worker = worker;
        this.running = plugin.getDescription().getVersion();
    }

    public void apply(ConfigurationSection updates) {
        enabled = updates.getBoolean("check", true);
        notifyStaff = updates.getBoolean("notify-staff", true);
        project = updates.getString("project", "").trim();
        url = updates.getString("url", "").trim();
        source = parseSource(updates.getString("source", "modrinth"));
        if (!enabled) {
            newer = "";
        }
    }

    /** Schedules the first check shortly after startup and one a day after that. */
    public void start() {
        task.cancel();
        task = schedulers.globalTimer(this::check, FIRST_TICKS, DAILY_TICKS);
    }

    public void shutdown() {
        task.cancel();
        task = ChorusTask.NONE;
    }

    /** The version waiting to be installed, or an empty string when there is none. */
    public String newerVersion() {
        return newer;
    }

    public boolean enabled() {
        return enabled;
    }

    /** One round, off the server thread. */
    public void check() {
        if (!enabled) {
            return;
        }
        worker.execute(() -> {
            String latest = ask();
            if (latest.isEmpty() || !Versions.isNewer(latest, running)) {
                return;
            }
            newer = latest;
            plugin.getLogger().info("ChorusCore " + latest + " is out. This server is on "
                    + running + ".");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!notifyStaff || newer.isEmpty() || !event.getPlayer().hasPermission(PERMISSION)) {
            return;
        }
        messages.send(event.getPlayer(), "core.update-available",
                "current", running, "latest", newer);
    }

    private String ask() {
        String endpoint = endpoint();
        if (endpoint.isEmpty()) {
            return "";
        }

        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "ChorusCore/" + running)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "";
            }
            return read(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception unreachable) {
            plugin.getLogger().log(Level.FINE, "The update check did not get an answer",
                    unreachable);
            return "";
        }
    }

    private String read(String body) {
        return switch (source) {
            case MODRINTH -> first(MODRINTH, body);
            case HANGAR -> first(HANGAR, body);
            // Both answer with the version on its own, so anything that is not one is an
            // error page and is thrown away rather than reported as a release.
            case SPIGOT, PLAIN -> clean(body);
        };
    }

    private String endpoint() {
        if (source == Source.PLAIN) {
            return url;
        }
        if (project.isEmpty()) {
            return "";
        }
        String id = URLEncoder.encode(project, StandardCharsets.UTF_8);
        return switch (source) {
            case MODRINTH -> "https://api.modrinth.com/v2/project/" + id + "/version";
            case HANGAR -> "https://hangar.papermc.io/api/v1/projects/" + id + "/versions?limit=1";
            case SPIGOT -> "https://api.spigotmc.org/legacy/update.php?resource=" + id;
            case PLAIN -> url;
        };
    }

    private static String first(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private static String clean(String raw) {
        String text = raw.trim();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).trim();
        }
        return PLAIN_VERSION.matcher(text).matches() ? text : "";
    }

    private static Source parseSource(String name) {
        try {
            return Source.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Source.MODRINTH;
        }
    }
}
