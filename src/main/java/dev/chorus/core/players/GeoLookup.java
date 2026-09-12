package dev.chorus.core.players;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Which country an address is in, for {@code /whois}.
 *
 * <p><b>Off by default, and for a reason.</b> There is no country in the player's connection;
 * the only way to know is to ask somebody else, which means sending that player's address to
 * a service outside this server. A server owner should decide that deliberately, so the
 * config says so plainly and starts switched off.
 *
 * <p>Answers are cached for the life of the server. The same address does not move country
 * between two uses of {@code /whois}, and the point of a cache here is to send as little as
 * possible rather than to be fast.
 */
public final class GeoLookup {

    private static final String DEFAULT_ENDPOINT = "https://ipapi.co/%address%/country_name/";
    private static final int MAX_LENGTH = 64;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final Executor worker;
    private final Map<String, String> known = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile String endpoint = DEFAULT_ENDPOINT;
    private volatile HttpClient client;

    public GeoLookup(Executor worker) {
        this.worker = worker;
    }

    public void apply(ConfigurationSection players) {
        ConfigurationSection geo = players.getConfigurationSection("geoip");
        enabled = geo != null && geo.getBoolean("enabled", false);
        endpoint = geo == null ? DEFAULT_ENDPOINT : geo.getString("endpoint", DEFAULT_ENDPOINT);
        if (enabled && client == null) {
            client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * The country for an address.
     *
     * <p>Completes with an empty string when it is switched off, when the address is not one
     * that can be looked up, or when the service does not answer. Nothing here is worth an
     * error message: a missing country is a missing line, not a fault.
     */
    public CompletableFuture<String> countryOf(String address) {
        if (!enabled || address.isEmpty() || isLocal(address)) {
            return CompletableFuture.completedFuture("");
        }
        String cached = known.get(address);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<String> answer = new CompletableFuture<>();
        worker.execute(() -> answer.complete(ask(address)));
        return answer;
    }

    public void clear() {
        known.clear();
    }

    private String ask(String address) {
        HttpClient http = client;
        if (http == null) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.replace("%address%", address)))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "ChorusCore")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "";
            }
            String country = clean(response.body());
            if (!country.isEmpty()) {
                known.put(address, country);
            }
            return country;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception unreachable) {
            return "";
        }
    }

    /** One line of plain text, with anything that is not a country name thrown away. */
    private static String clean(String body) {
        String text = body.trim();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).trim();
        }
        if (text.length() > MAX_LENGTH || text.startsWith("{") || text.startsWith("<")) {
            return "";
        }
        return text.replaceAll("[^\\p{L} .'-]", "");
    }

    /** Nobody outside can tell you where a private address is, so nothing is sent for one. */
    private static boolean isLocal(String address) {
        return address.startsWith("127.")
                || address.startsWith("10.")
                || address.startsWith("192.168.")
                || address.startsWith("172.16.")
                || address.startsWith("::1")
                || address.equals("localhost");
    }
}
