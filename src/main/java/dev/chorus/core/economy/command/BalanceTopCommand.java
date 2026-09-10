package dev.chorus.core.economy.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.EconomyService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The richest players who are online.
 *
 * <p>Deliberately not the whole server: balances belong to whichever economy plugin is
 * installed, and the only way to rank everyone who ever played would be to ask it about each
 * of them in turn, on the server thread. Plenty of servers have found out the hard way what
 * that does to a Sunday afternoon.
 */
public final class BalanceTopCommand extends ChorusCommand {

    private final Economy economy;
    private final EconomyService service;

    public BalanceTopCommand(CommandSupport support, Economy economy, EconomyService service) {
        super(support, "baltop", "chorus.economy.baltop");
        this.economy = economy;
        this.service = service;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        List<Entry> entries = new ArrayList<>();
        for (Player online : sender.getServer().getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(online)) {
                continue;
            }
            entries.add(new Entry(online.getName(), economy.balance(online)));
        }
        entries.sort(Comparator.comparingDouble(Entry::balance).reversed());

        settle(sender);
        if (entries.isEmpty()) {
            messages.send(sender, "economy.baltop-empty");
            return;
        }

        int shown = Math.min(entries.size(), service.settings().topSize());
        messages.send(sender, "economy.baltop-header", "count", String.valueOf(shown));
        for (int place = 0; place < shown; place++) {
            Entry entry = entries.get(place);
            messages.send(sender, "economy.baltop-entry",
                    "place", String.valueOf(place + 1),
                    "player", entry.name(),
                    "amount", economy.format(entry.balance()));
        }
    }

    private record Entry(String name, double balance) {
    }
}
