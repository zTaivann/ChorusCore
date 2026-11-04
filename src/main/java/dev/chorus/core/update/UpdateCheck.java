package dev.chorus.core.update;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Asks once a day whether a newer ChorusCore has been released. */
public final class UpdateCheck implements Listener {

    private static final String PERMISSION = "chorus.updates";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final long FIRST_TICKS = 20L * 30;
    private static final long DAILY_TICKS = 20L * 60 * 60 * 24;
    private static final Pattern VERSION = Pattern.compile("^[vV]?[0-9][0-9A-Za-z.\\-_+]{0,31}$");

    /** Modrinth lists its versions newest first, so the first one is the latest. */
    private static final Pattern LISTED_VERSION =
            Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private static final String SPIGOT_ANSWER =
            "https://api.spigotmc.org/legacy/update.php?resource=138794";

    /** Where ChorusCore is published: where to ask, and where to send staff for the download. */
    public enum Source {

        MODRINTH("Modrinth", "https://api.modrinth.com/v2/project/chorus-core/version",
                "https://modrinth.com/plugin/chorus-core"),

        SPIGOT("SpigotMC", SPIGOT_ANSWER, "https://www.spigotmc.org/resources/138794/"),

        BUILTBYBIT("BuiltByBit", SPIGOT_ANSWER,
                "https://builtbybit.com/resources/chorus-core.126131/");

        private final String site;
        private final String answer;
        private final String page;

        Source(String site, String answer, String page) {
            this.site = site;
            this.answer = answer;
            this.page = page;
        }

        public String site() {
            return site;
        }

        /** An address that answers with the newest version and nothing else. */
        public String answer() {
            return answer;
        }

        /** Where the download is. */
        public String page() {
            return page;
        }

        /** Null when the text names none of them. */
        public static @Nullable Source of(String name) {
            String wanted = name.trim().toUpperCase(Locale.ROOT).replace("-", "");
            for (Source source : values()) {
                if (source.name().equals(wanted)) {
                    return source;
                }
            }
            return null;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Schedulers schedulers;
    private final String running;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile boolean enabled;
    private volatile boolean notifyStaff = true;
    private volatile Source source = Source.MODRINTH;

    /** Empty until a check has found something later than what is running. */
    private volatile String newer = "";

    private ChorusTask task = ChorusTask.NONE;

    public UpdateCheck(Plugin plugin, Messages messages, Schedulers schedulers) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedulers = schedulers;
        this.running = plugin.getDescription().getVersion();
    }

    public void apply(ConfigurationSection updates) {
        enabled = updates.getBoolean("check", true);
        notifyStaff = updates.getBoolean("notify-staff", true);
        String written = updates.getString("source", "modrinth");
        Source chosen = Source.of(written);
        if (chosen == null) {
            plugin.getLogger().warning("updates.source '" + written + "' is not one of modrinth,"
                    + " spigot or builtbybit, so modrinth is used.");
            chosen = Source.MODRINTH;
        }
        source = chosen;
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

    /** One round, on the HTTP client's own threads. */
    public void check() {
        if (!enabled) {
            return;
        }
        Source asked = source;
        ask(asked).thenAccept(latest -> {
            if (latest.isEmpty() || !Versions.isNewer(latest, running)) {
                return;
            }
            newer = latest;
            plugin.getLogger().info("ChorusCore " + latest + " is out. This server is on "
                    + running + ". Download it from " + asked.page());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!notifyStaff || newer.isEmpty() || !event.getPlayer().hasPermission(PERMISSION)) {
            return;
        }
        Source from = source;
        event.getPlayer().sendMessage(messages.render(event.getPlayer(), "core.update-available",
                        "current", running, "latest", newer, "site", from.site())
                .clickEvent(ClickEvent.openUrl(from.page())));
    }

    private CompletableFuture<String> ask(Source asked) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(asked.answer()))
                .timeout(TIMEOUT)
                .header("User-Agent", "zTaivann/ChorusCore/" + running)
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200 ? clean(response.body()) : "")
                .exceptionally(unreachable -> {
                    plugin.getLogger().log(Level.FINE, "The update check did not get an answer",
                            unreachable);
                    return "";
                });
    }

    /** The version, or empty when the answer is not one. */
    static String clean(String raw) {
        String text = raw.trim();
        if (text.startsWith("[")) {
            Matcher listed = LISTED_VERSION.matcher(text);
            text = listed.find() ? listed.group(1) : "";
        }
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).trim();
        }
        return VERSION.matcher(text).matches() ? text : "";
    }
}
