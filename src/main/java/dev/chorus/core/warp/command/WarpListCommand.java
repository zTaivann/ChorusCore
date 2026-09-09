package dev.chorus.core.warp.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.menu.ListMenu;
import dev.chorus.core.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WarpListCommand extends ChorusCommand {

    private final WarpService warps;

    public WarpListCommand(CommandSupport support, WarpService warps) {
        super(support, "warps", "chorus.warp.list");
        this.warps = warps;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        List<NamedLocation> visible = warps.visibleTo(sender);
        if (visible.isEmpty()) {
            messages.send(sender, "warp.none");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        // The console has no screen to open, so it always gets the written list.
        if (warps.settings().menu().enabled() && sender instanceof Player player) {
            openMenu(player, visible);
        } else {
            sendList(sender, visible);
        }
    }

    private void openMenu(Player player, List<NamedLocation> visible) {
        List<ListMenu.Entry> entries = new ArrayList<>(visible.size());
        for (NamedLocation warp : visible) {
            entries.add(new ListMenu.Entry(
                    warps.settings().menu().icon(),
                    messages.render("warp.menu.entry", "warp", warp.name()),
                    List.of(
                            messages.render("warp.menu.lore-world", "world", warp.worldName()),
                            messages.render("warp.menu.lore-position",
                                    "x", round(warp.x()), "y", round(warp.y()), "z", round(warp.z())),
                            messages.render("warp.menu.lore-divider"),
                            messages.render("warp.menu.lore-action")),
                    clicker -> {
                        clicker.closeInventory();
                        clicker.performCommand("warp " + warp.name());
                    }));
        }
        ListMenu.open(player, messages, warps.settings().menu(), "warp.menu.title", entries, 0);
    }

    private void sendList(CommandSender sender, List<NamedLocation> visible) {
        messages.send(sender, "warp.list.header", "count", String.valueOf(visible.size()));

        List<Component> entries = new ArrayList<>(visible.size());
        for (NamedLocation warp : visible) {
            entries.add(messages.render("warp.list.entry", "warp", warp.name())
                    .clickEvent(ClickEvent.runCommand("/warp " + warp.name()))
                    .hoverEvent(HoverEvent.showText(location(warp))));
        }
        sender.sendMessage(Component.join(
                JoinConfiguration.separator(messages.render("warp.list.separator")), entries));
    }

    private Component location(NamedLocation warp) {
        return messages.render("warp.list.hover",
                "warp", warp.name(),
                "world", warp.worldName(),
                "x", round(warp.x()),
                "y", round(warp.y()),
                "z", round(warp.z()));
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
