package dev.chorus.core.kits.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.kits.Kit;
import dev.chorus.core.kits.KitService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /kitreset <kit|*> [player]}: clears a cooldown, or lets a one-time kit be taken
 * again.
 *
 * <p>The player has to be online, since the record of what they have taken is only loaded
 * while they are.
 */
public final class KitResetCommand extends ChorusCommand {

    private final KitService kits;

    public KitResetCommand(CommandSupport support, KitService kits) {
        super(support, "kitreset", "chorus.kits.reset");
        this.kits = kits;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "kits.reset-usage");
            return;
        }

        Player target;
        if (args.length > 1) {
            target = online(sender, args[1]);
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "kits.reset-usage");
            return;
        }
        if (target == null) {
            return;
        }
        if (!kits.isLoaded(target.getUniqueId())) {
            messages.send(sender, "kits.reset-not-ready", "player", target.getName());
            return;
        }

        if (args[0].equals("*") || args[0].equalsIgnoreCase("all")) {
            resetAll(sender, target);
            return;
        }

        Kit kit = kits.find(args[0]).orElse(null);
        if (kit == null) {
            messages.send(sender, "kits.unknown", "kit", args[0]);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        kits.reset(target.getUniqueId(), kit.name()).whenComplete((cleared, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (!Boolean.TRUE.equals(cleared)) {
                messages.send(sender, "kits.reset-nothing",
                        "kit", kit.name(), "player", target.getName());
                return;
            }
            settle(sender);
            messages.send(sender, "kits.reset-done", "kit", kit.name(), "player", target.getName());
            if (!target.equals(sender)) {
                messages.send(target, "kits.reset-received", "kit", kit.name());
            }
        });
    }

    private void resetAll(CommandSender sender, Player target) {
        List<Kit> all = kits.all();
        if (all.isEmpty()) {
            messages.send(sender, "kits.none");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        all.forEach(kit -> kits.reset(target.getUniqueId(), kit.name()));
        settle(sender);
        messages.send(sender, "kits.reset-all",
                "count", String.valueOf(all.size()), "player", target.getName());
        if (!target.equals(sender)) {
            messages.send(target, "kits.reset-all-received");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(List.of("*"));
            kits.all().forEach(kit -> names.add(kit.name()));
            return startingWith(args[0].toLowerCase(Locale.ROOT), names);
        }
        return args.length == 2 ? onlineNames(sender, args[1], true) : List.of();
    }
}
