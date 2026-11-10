package dev.chorus.core.items.command;

import dev.chorus.commons.command.Durations;
import dev.chorus.core.backup.BackupMenu;
import dev.chorus.core.backup.BackupReason;
import dev.chorus.core.backup.InventoryBackup;
import dev.chorus.core.backup.InventoryBackups;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.players.PlayerProfiles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** {@code /restore <player> [list|number]}: an inventory as it was before something wiped it. */
public final class RestoreCommand extends ChorusCommand {

    /**
     * Enough to open the menu and look; restoring needs {@code chorus.items.restore}, which
     * plugin.yml grants with it.
     */
    private static final String VIEW_PERMISSION = "chorus.items.restore.view";

    private static final String RESTORE_PERMISSION = "chorus.items.restore";

    private final InventoryBackups backups;
    private final BackupMenu menu;
    private final PlayerProfiles profiles;

    public RestoreCommand(CommandSupport support, InventoryBackups backups, BackupMenu menu,
                          PlayerProfiles profiles) {
        super(support, "restore", VIEW_PERMISSION);
        this.backups = backups;
        this.menu = menu;
        this.profiles = profiles;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!backups.enabled()) {
            messages.send(sender, "items.restore-disabled");
            return;
        }
        if (args.length == 0) {
            recent(sender);
            return;
        }

        Player online = sender.getServer().getPlayerExact(args[0]);
        if (online != null && (!(sender instanceof Player viewer) || viewer.canSee(online))) {
            open(sender, online.getUniqueId(), online.getName(), args);
            return;
        }

        profiles.find(args[0]).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "error.player-not-found", "player", args[0]);
                return;
            }
            open(sender, found.get().player(), found.get().name(), args);
        });
    }

    /** The newest backups from anybody, for a report that does not name who. */
    private void recent(CommandSender sender) {
        if (!ready(sender)) {
            return;
        }
        backups.recent().whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "items.restore-nothing-recent");
                return;
            }

            settle(sender);
            messages.send(sender, "items.restore-recent-header",
                    "count", String.valueOf(found.size()));
            for (InventoryBackup backup : found) {
                messages.send(sender, "items.restore-recent-entry",
                        "player", ownerName(sender, backup),
                        "reason", BackupReason.describe(backup.reason(), messages, sender),
                        "world", backup.world(),
                        "ago", Durations.format(System.currentTimeMillis() - backup.takenAt()));
            }
            messages.send(sender, "items.restore-usage");
        });
    }

    private void open(CommandSender sender, UUID owner, String name, String[] args) {
        if (!ready(sender)) {
            return;
        }

        boolean listing = args.length > 1 && args[1].equalsIgnoreCase("list");
        int wanted = args.length > 1 ? number(args) : -2;
        if (!listing && wanted == -1) {
            messages.send(sender, "items.restore-usage");
            return;
        }

        backups.find(owner).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "items.restore-none", "player", name);
                return;
            }
            if (listing) {
                list(sender, name, found);
                return;
            }

            Player target = sender.getServer().getPlayer(owner);
            if (wanted == -2) {
                if (sender instanceof Player viewer && target != null) {
                    onPlayer(viewer, () -> menu.openList(viewer, target, found));
                } else {
                    list(sender, name, found);
                }
                return;
            }
            if (wanted >= found.size()) {
                messages.send(sender, "items.restore-unknown",
                        "number", String.valueOf(wanted + 1),
                        "count", String.valueOf(found.size()));
                return;
            }
            restore(sender, owner, name, found.get(wanted));
        });
    }

    private void restore(CommandSender sender, UUID owner, String name,
                         InventoryBackup backup) {
        if (!sender.hasPermission(RESTORE_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }

        String ago = Durations.format(System.currentTimeMillis() - backup.takenAt());
        Player target = sender.getServer().getPlayer(owner);
        if (target == null) {
            backups.queue(owner, backup.id(), EnumSet.allOf(InventoryBackups.Part.class),
                    sender.getName());
            settle(sender);
            messages.send(sender, "items.restore-queued", "player", name, "ago", ago);
            return;
        }

        // Done to the player, so it runs on the thread that owns them.
        onPlayer(target, () -> {
            if (backups.restore(target, backup,
                    EnumSet.allOf(InventoryBackups.Part.class), sender.getName()).isEmpty()) {
                messages.send(sender, "items.restore-unreadable");
                return;
            }

            settle(sender);
            messages.send(sender, "items.restored", "player", name, "ago", ago);
            if (!sender.equals(target)) {
                messages.send(target, "items.restore-received");
            }
        });
    }

    private void list(CommandSender sender, String name, List<InventoryBackup> found) {
        messages.send(sender, "items.restore-header",
                "player", name, "count", String.valueOf(found.size()));
        for (int index = 0; index < found.size(); index++) {
            InventoryBackup backup = found.get(index);
            messages.send(sender, "items.restore-entry",
                    "number", String.valueOf(index + 1),
                    "reason", BackupReason.describe(backup.reason(), messages, sender),
                    "actor", backup.actor(),
                    "ago", Durations.format(System.currentTimeMillis() - backup.takenAt()));
        }
        messages.send(sender, "items.restore-footer", "player", name);
    }

    /** The number as it was listed, counting from one. -1 when it is not a number at all. */
    private static int number(String[] args) {
        if (args.length < 2) {
            return 0;
        }
        int typed = Numbers.integer(args[1], 0);
        return typed < 1 ? -1 : typed - 1;
    }

    /** Whose inventory it was, by the name the server remembers. */
    private static String ownerName(CommandSender sender, InventoryBackup backup) {
        String name = sender.getServer().getOfflinePlayer(backup.owner()).getName();
        return name == null ? backup.owner().toString() : name;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return onlineNames(sender, args[0], true);
        }
        if (args.length == 2) {
            return startingWith(args[1], List.of("list", "1", "2", "3"));
        }
        return List.of();
    }
}
