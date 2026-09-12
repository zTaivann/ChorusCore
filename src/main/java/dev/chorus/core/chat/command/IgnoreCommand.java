package dev.chorus.core.chat.command;

import dev.chorus.core.chat.IgnoreList;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@code /ignore <player>} and {@code /ignore list}: who not to hear from. */
public final class IgnoreCommand extends PlayerCommand {

    private final IgnoreList ignores;

    public IgnoreCommand(CommandSupport support, IgnoreList ignores) {
        super(support, "ignore", "chorus.chat.ignore");
        this.ignores = ignores;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "chat.ignore-usage");
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            list(player);
            return;
        }

        OfflinePlayer target = known(player, args[0]);
        if (target == null) {
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "chat.ignore-self");
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean ignored = ignores.toggle(player.getUniqueId(), target.getUniqueId());
        settle(player);
        messages.send(player, ignored ? "chat.ignore-added" : "chat.ignore-removed",
                "player", name(target.getUniqueId(), args[0]));
    }

    private void list(Player player) {
        List<UUID> ignored = new ArrayList<>(ignores.listOf(player.getUniqueId()));
        if (ignored.isEmpty()) {
            messages.send(player, "chat.ignore-empty");
            return;
        }

        List<Component> entries = new ArrayList<>(ignored.size());
        for (UUID id : ignored) {
            entries.add(messages.render("chat.ignore-entry", "player", name(id, id.toString())));
        }
        messages.send(player, "chat.ignore-header", "count", String.valueOf(entries.size()));
        player.sendMessage(Component.join(
                JoinConfiguration.separator(messages.render("chat.ignore-separator")), entries));
    }

    /** The name the server remembers, or whatever was typed when it remembers none. */
    private static String name(UUID id, String fallback) {
        String known = Bukkit.getOfflinePlayer(id).getName();
        return known == null ? fallback : known;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>(onlineNames(sender, args[0], false));
        names.add("list");
        return startingWith(args[0], names);
    }
}
