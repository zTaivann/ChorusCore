package dev.chorus.core.kits.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.kits.Kit;
import dev.chorus.core.kits.KitService;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KitCommand extends PlayerCommand {

    private final KitService kits;
    private final Logger logger;

    public KitCommand(CommandSupport support, KitService kits, Logger logger) {
        super(support, "kit", "chorus.kits.use");
        this.kits = kits;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!kits.isLoaded(player.getUniqueId())) {
            messages.send(player, "error.loading");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "kits.usage");
            return;
        }

        Optional<Kit> found = kits.find(args[0]);
        if (found.isEmpty()) {
            messages.send(player, "kits.unknown", "kit", args[0]);
            return;
        }

        Kit kit = found.get();
        // Reported as missing rather than forbidden, so /kit lists nobody else's kits.
        if (!kit.allowed(player)) {
            messages.send(player, "kits.unknown", "kit", args[0]);
            return;
        }
        if (!claimable(player, kit)) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        kits.give(player, kit).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not give the kit " + kit.name()
                        + " to " + player.getName(), failure);
                messages.send(player, "error.storage");
                return;
            }
            settle(player);
            messages.send(player, "kits.received", "kit", kit.name());
        });
    }

    /** Every reason a kit might be refused, in the order a player would ask them. */
    private boolean claimable(Player player, Kit kit) {
        long left = kits.remaining(player.getUniqueId(), kit, System.currentTimeMillis());
        if (left == Long.MAX_VALUE) {
            refuse(player, kit);
            messages.send(player, kit.oneTime() ? "kits.one-time" : "kits.spent",
                    "kit", kit.name());
            return false;
        }
        if (left > 0) {
            refuse(player, kit);
            messages.send(player, "kits.cooldown", "kit", kit.name(), "time", Durations.format(left));
            return false;
        }

        Requirement unmet = kits.unmet(player, kit);
        if (unmet == null) {
            return true;
        }
        refuse(player, kit);
        unmet.tell(player, messages, "kits.requirement", "kit", kit.name());
        return false;
    }

    private void refuse(Player player, Kit kit) {
        Action.runAll(kit.failActions(), player, messages, schedulers, "kit", kit.name());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return startingWith(args[0], kits.visibleTo(player).stream().map(Kit::name).toList());
    }
}
