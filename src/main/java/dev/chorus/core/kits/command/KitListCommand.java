package dev.chorus.core.kits.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.kits.Kit;
import dev.chorus.core.kits.KitService;
import dev.chorus.core.kits.KitSettings;
import dev.chorus.core.menu.ListMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class KitListCommand extends PlayerCommand {

    private final KitService kits;
    private final Supplier<KitSettings> settings;

    public KitListCommand(CommandSupport support, KitService kits, Supplier<KitSettings> settings) {
        super(support, "kits", "chorus.kits.list");
        this.kits = kits;
        this.settings = settings;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!kits.isLoaded(player.getUniqueId())) {
            messages.send(player, "error.loading");
            return;
        }

        List<Kit> visible = kits.visibleTo(player);
        if (visible.isEmpty()) {
            messages.send(player, "kits.none");
            return;
        }
        if (!ready(player)) {
            return;
        }
        settle(player);

        if (settings.get().menu().enabled()) {
            openMenu(player, visible);
        } else {
            sendList(player, visible);
        }
    }

    private void openMenu(Player player, List<Kit> visible) {
        long now = System.currentTimeMillis();
        List<ListMenu.Entry> entries = new ArrayList<>(visible.size());
        for (Kit kit : visible) {
            List<Component> lore = new ArrayList<>(kit.lore());
            lore.add(messages.render("menu.kits.lore-divider"));
            lore.add(status(kit, player, now));
            entries.add(new ListMenu.Entry(kit.icon(), kit.display(), lore, clicker -> {
                clicker.closeInventory();
                clicker.performCommand("kit " + kit.name());
            }));
        }
        ListMenu.open(player, messages, settings.get().menu(), "menu.kits.title", entries, 0);
    }

    private Component status(Kit kit, Player player, long now) {
        long left = kits.remaining(player.getUniqueId(), kit, now);
        if (left == Long.MAX_VALUE) {
            return messages.render("menu.kits.lore-taken");
        }
        if (left > 0) {
            return messages.render("menu.kits.lore-cooldown", "time", Durations.format(left));
        }
        return messages.render("menu.kits.lore-action");
    }

    private void sendList(Player player, List<Kit> visible) {
        messages.send(player, "kits.list-header", "count", String.valueOf(visible.size()));

        List<Component> entries = new ArrayList<>(visible.size());
        for (Kit kit : visible) {
            entries.add(messages.render("kits.list-entry", "kit", kit.name())
                    .clickEvent(ClickEvent.runCommand("/kit " + kit.name())));
        }
        player.sendMessage(Component.join(
                JoinConfiguration.separator(messages.render("kits.list-separator")), entries));
    }
}
