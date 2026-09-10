package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.menu.ListMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HomeListCommand extends PlayerCommand {

    private static final String UNLIMITED = "∞";

    private final HomeService homes;

    public HomeListCommand(CommandSupport support, HomeService homes) {
        super(support, "homes", "chorus.home.list");
        this.homes = homes;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!homes.isLoaded(player.getUniqueId())) {
            messages.send(player, "error.loading");
            return;
        }

        List<Home> owned = homes.list(player.getUniqueId());
        if (owned.isEmpty()) {
            messages.send(player, "home.none");
            return;
        }
        if (!ready(player)) {
            return;
        }
        settle(player);

        if (homes.settings().menu().enabled()) {
            openMenu(player, owned);
        } else {
            sendList(player, owned);
        }
    }

    private void openMenu(Player player, List<Home> owned) {
        List<ListMenu.Entry> entries = new ArrayList<>(owned.size());
        for (Home home : owned) {
            entries.add(new ListMenu.Entry(
                    icon(home),
                    messages.render("home.menu.entry", "home", home.name()),
                    List.of(
                            messages.render("home.menu.lore-world", "world", home.worldName()),
                            messages.render("home.menu.lore-position",
                                    "x", round(home.x()), "y", round(home.y()), "z", round(home.z())),
                            messages.render("home.menu.lore-divider"),
                            messages.render("home.menu.lore-action")),
                    clicker -> {
                        clicker.closeInventory();
                        clicker.performCommand("home " + home.name());
                    }));
        }
        ListMenu.open(player, messages, homes.settings().menu(), "home.menu.title", entries, 0);
    }

    private void sendList(Player player, List<Home> owned) {
        int limit = homes.limit(player);
        messages.send(player, "home.list.header",
                "count", String.valueOf(owned.size()),
                "limit", limit == Integer.MAX_VALUE ? UNLIMITED : String.valueOf(limit));

        List<Component> entries = new ArrayList<>(owned.size());
        for (Home home : owned) {
            entries.add(entry(home));
        }
        player.sendMessage(Component.join(
                JoinConfiguration.separator(messages.render("home.list.separator")), entries));
    }

    /**
     * The click and hover live in code rather than in messages.yml because the home name has
     * to reach the command as data, not as part of a template someone could reshape.
     */
    private Component entry(Home home) {
        return messages.render("home.list.entry", "home", home.name())
                .clickEvent(ClickEvent.runCommand("/home " + home.name()))
                .hoverEvent(HoverEvent.showText(location(home)));
    }

    private Component location(Home home) {
        return messages.render("home.list.hover",
                "home", home.name(),
                "world", home.worldName(),
                "x", round(home.x()),
                "y", round(home.y()),
                "z", round(home.z()));
    }

    /** The home's own icon when it has one, and the one from the config when it does not. */
    private Material icon(Home home) {
        if (home.icon() == null) {
            return homes.settings().menu().icon();
        }
        Material own = Material.matchMaterial(home.icon());
        return own != null && own.isItem() ? own : homes.settings().menu().icon();
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
