package dev.chorus.core.kits.rules;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * One thing that happens when a kit is claimed, or when a claim is refused.
 *
 * <p>Written as {@code type: argument}, which keeps a whole sequence readable in the file
 * and means adding a new kind of action never changes the shape of anybody's config:
 *
 * <pre>
 * claim-actions:
 *   - 'message: &lt;green&gt;Enjoy your %kit% kit.'
 *   - 'broadcast: &lt;gray&gt;%player% just claimed %kit%.'
 *   - 'sound: entity.player.levelup 1 1.4'
 *   - 'title: &lt;gold&gt;&lt;bold&gt;VIP;&lt;gray&gt;Welcome aboard'
 *   - 'console: lp user %player% parent add vip'
 *   - 'close'
 * </pre>
 */
public record KitAction(Kind kind, String argument) {

    public enum Kind {
        MESSAGE, BROADCAST, ACTIONBAR, TITLE, SOUND, CONSOLE, PLAYER, CLOSE
    }

    private static final long TITLE_FADE_MILLIS = 300;
    private static final long TITLE_STAY_MILLIS = 2500;

    /** Skips anything unusable rather than refusing the whole kit over one typo. */
    public static List<KitAction> read(List<String> lines, String kit, Consumer<String> onProblem) {
        List<KitAction> actions = new ArrayList<>(lines.size());
        for (String line : lines) {
            KitAction action = parse(line);
            if (action == null) {
                onProblem.accept("kit '" + kit + "' has an action this plugin does not know: '"
                        + line + "'");
                continue;
            }
            actions.add(action);
        }
        return List.copyOf(actions);
    }

    private static @Nullable KitAction parse(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        String name = (colon < 0 ? trimmed : trimmed.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
        String argument = colon < 0 ? "" : trimmed.substring(colon + 1).trim();

        for (Kind kind : Kind.values()) {
            if (kind.name().equals(name)) {
                return new KitAction(kind, argument);
            }
        }
        return null;
    }

    public static void runAll(List<KitAction> actions, Player player, Messages messages, String kit) {
        for (KitAction action : actions) {
            action.run(player, messages, kit);
        }
    }

    public void run(Player player, Messages messages, String kit) {
        String filled = Placeholders.fill(player, argument.replace("%kit%", kit));
        switch (kind) {
            case MESSAGE -> player.sendMessage(messages.parse(filled));
            case BROADCAST -> Bukkit.getServer().sendMessage(messages.parse(filled));
            case ACTIONBAR -> player.sendActionBar(messages.parse(filled));
            case TITLE -> player.showTitle(title(messages, filled));
            case SOUND -> playSound(player, filled);
            case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled);
            case PLAYER -> player.performCommand(filled);
            case CLOSE -> player.closeInventory();
        }
    }

    /** {@code title: main;subtitle}, the semicolon being optional. */
    private static Title title(Messages messages, String filled) {
        int split = filled.indexOf(';');
        Component main = messages.parse(split < 0 ? filled : filled.substring(0, split));
        Component sub = split < 0 ? Component.empty() : messages.parse(filled.substring(split + 1));
        return Title.title(main, sub, Title.Times.times(
                Duration.ofMillis(TITLE_FADE_MILLIS),
                Duration.ofMillis(TITLE_STAY_MILLIS),
                Duration.ofMillis(TITLE_FADE_MILLIS)));
    }

    /**
     * {@code sound: key [volume] [pitch]}.
     *
     * <p>Played by name rather than through the {@link Sound} constants, which were renamed
     * between the versions this jar runs on. A name the client does not know simply plays
     * nothing, so a typo can never break anything.
     */
    private static void playSound(Player player, String filled) {
        String[] parts = filled.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        player.playSound(player.getLocation(), parts[0],
                parts.length > 1 ? number(parts[1], 1f) : 1f,
                parts.length > 2 ? number(parts[2], 1f) : 1f);
    }

    private static float number(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
