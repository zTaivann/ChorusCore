package dev.chorus.core.players;

import org.bukkit.configuration.ConfigurationSection;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Which country an address is in, for {@code /whois}. */
public final class GeoLookup {

    private static final String DEFAULT_ENDPOINT = "https://ipapi.co/%address%/country_name/";
    private static final int MAX_LENGTH = 64;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Pattern LITERAL =
            Pattern.compile("[0-9.]+|[0-9a-fA-F:.%]*:[0-9a-fA-F:.%]*");

    private final Map<String, String> known = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile String endpoint = DEFAULT_ENDPOINT;
    private volatile HttpClient client;

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

    /** The country for an address, asked on the HTTP client's own threads. */
    public CompletableFuture<String> countryOf(String address) {
        HttpClient http = client;
        if (!enabled || http == null || address.isEmpty() || isLocal(address)) {
            return CompletableFuture.completedFuture("");
        }
        String cached = known.get(address);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.replace("%address%",
                            URLEncoder.encode(address, StandardCharsets.UTF_8))))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "ChorusCore")
                    .GET()
                    .build();
        } catch (IllegalArgumentException badEndpoint) {
            return CompletableFuture.completedFuture("");
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String country = response.statusCode() == 200 ? clean(response.body()) : "";
                    if (!country.isEmpty()) {
                        known.put(address, country);
                    }
                    return country;
                })
                .exceptionally(unreachable -> "");
    }

    public void clear() {
        known.clear();
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
        // Anything that is not written as an address is never sent, and never looked up.
        if (!LITERAL.matcher(address).matches()) {
            return true;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return true;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress()
                    || parsed.isLinkLocalAddress() || parsed.isAnyLocalAddress();
        } catch (UnknownHostException | SecurityException unreadable) {
            return true;
        }
    }
}
