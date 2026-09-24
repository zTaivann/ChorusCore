package dev.chorus.core.papi;

import dev.chorus.core.api.ChorusApi;
import dev.chorus.core.api.HomeApi;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.AfkService;
import dev.chorus.core.players.Playtime;
import dev.chorus.core.staff.VanishService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What every {@code %chorus_...%} placeholder answers. Nothing here touches PlaceholderAPI. */
public final class PlaceholderValues {

    /** The plugin these are exposed through, where it is installed. */
    public static final String PLUGIN = "PlaceholderAPI";

    /** Every placeholder, without the {@code chorus_} in front. */
    public static final List<String> NAMES = List.of(
            "afk", "balance", "balance_raw", "homes_free", "homes_limit", "homes_used",
            "playtime", "spawn_set", "vanished", "version", "warps_available", "warps_total");

    private final ChorusApi api;
    private final @Nullable AfkService afk;
    private final @Nullable VanishService vanish;

    public PlaceholderValues(ChorusApi api, @Nullable AfkService afk,
                             @Nullable VanishService vanish) {
        this.api = api;
        this.afk = afk;
        this.vanish = vanish;
    }

    /** Null when the placeholder is not one of ours, or the player is not here to answer it. */
    public @Nullable String of(@Nullable OfflinePlayer player, String parameters) {
        String id = parameters.toLowerCase(Locale.ROOT);

        if (id.equals("warps_total")) {
            return api.warps().map(warps -> String.valueOf(warps.all().size())).orElse("0");
        }
        if (id.equals("version")) {
            return api.version();
        }
        if (player == null) {
            return null;
        }

        switch (id) {
            case "playtime" -> {
                return Durations.format(Playtime.of(player));
            }
            case "balance" -> {
                return api.economy().enabled()
                        ? api.economy().format(api.economy().balance(player)) : "";
            }
            case "balance_raw" -> {
                return String.format(Locale.ROOT, "%.2f", api.economy().balance(player));
            }
            default -> {
                // The rest all need the player to actually be here.
            }
        }

        Player online = player.getPlayer();
        if (online == null) {
            return null;
        }
        return switch (id) {
            case "homes_used" -> homes()
                    .map(homes -> String.valueOf(homes.count(online.getUniqueId()))).orElse("0");
            case "homes_limit" -> homes().map(homes -> limit(homes.limit(online))).orElse("0");
            case "homes_free" -> homes().map(homes -> free(homes, online)).orElse("0");
            case "warps_available" -> api.warps()
                    .map(warps -> String.valueOf(warps.visibleTo(online).size())).orElse("0");
            case "spawn_set" -> api.spawns()
                    .map(spawns -> yesNo(spawns.find(online.getWorld()).isPresent())).orElse("false");
            case "afk" -> yesNo(afk != null && afk.isAway(online.getUniqueId()));
            case "vanished" -> yesNo(vanish != null && vanish.isVanished(online.getUniqueId()));
            default -> null;
        };
    }

    private Optional<HomeApi> homes() {
        return api.homes();
    }

    private static String free(HomeApi homes, Player player) {
        int limit = homes.limit(player);
        if (limit == Integer.MAX_VALUE) {
            return "∞";
        }
        return String.valueOf(Math.max(0, limit - homes.count(player.getUniqueId())));
    }

    private static String limit(int value) {
        return value == Integer.MAX_VALUE ? "∞" : String.valueOf(value);
    }

    private static String yesNo(boolean value) {
        return value ? "true" : "false";
    }
}
