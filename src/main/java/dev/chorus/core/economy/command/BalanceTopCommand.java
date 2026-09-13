package dev.chorus.core.economy.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.economy.Balances;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.EconomyService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The richest players on the server.
 *
 * <p>Every account the built-in ledger holds, ranked and paged, since it is all in memory
 * already. On a server whose money belongs to another plugin there is no such list to read:
 * the only way to rank everyone who ever played would be to ask that plugin about each of
 * them in turn, on the server thread, so the ranking falls back to whoever is online.
 */
public final class BalanceTopCommand extends ChorusCommand {

    private static final String REFRESH = "refresh";
    private static final String ADMIN = "chorus.economy.admin";

    private final Economy economy;
    private final EconomyService service;
    private final @Nullable Balances ledger;

    public BalanceTopCommand(CommandSupport support, Economy economy, EconomyService service,
                             @Nullable Balances ledger) {
        super(support, "baltop", "chorus.economy.baltop");
        this.economy = economy;
        this.service = service;
        this.ledger = ledger;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase(REFRESH)) {
            refresh(sender);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        int size = service.settings().topSize();
        int page = Math.max(1, args.length > 0 ? Numbers.integer(args[0], 1) : 1);
        int total = ledger == null ? onlineCount(sender) : ledger.size();
        int pages = Math.max(1, (total + size - 1) / size);
        if (page > pages) {
            page = pages;
        }

        List<Entry> entries = ledger == null
                ? online(sender, size, (page - 1) * size)
                : ranked(size, (page - 1) * size);

        settle(sender);
        if (entries.isEmpty()) {
            messages.send(sender, "economy.baltop-empty");
            return;
        }

        messages.send(sender, "economy.baltop-header",
                "count", String.valueOf(total),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages));
        int place = (page - 1) * size;
        for (Entry entry : entries) {
            messages.send(sender, "economy.baltop-entry",
                    "place", String.valueOf(++place),
                    "player", entry.name(),
                    "amount", economy.format(entry.balance()));
        }
        if (ledger != null) {
            messages.send(sender, "economy.baltop-total", "amount", economy.format(ledger.total()));
        }
    }

    /**
     * Throws the ranking away.
     *
     * <p>It is worked out from memory and kept for half a minute, which is almost always what
     * you want. The exception is a server whose balances another plugin changed behind this
     * one's back, where the list can be right and look wrong until it expires.
     */
    private void refresh(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (ledger == null) {
            messages.send(sender, "economy.baltop-refresh-external");
            return;
        }
        ledger.refresh();
        messages.send(sender, "economy.baltop-refreshed");
    }

    private List<Entry> ranked(int size, int offset) {
        List<Entry> entries = new ArrayList<>(size);
        for (Balances.Ranked account : ledger.top(size, offset)) {
            entries.add(new Entry(account.name(), account.balance()));
        }
        return entries;
    }

    private List<Entry> online(CommandSender sender, int size, int offset) {
        List<Entry> entries = new ArrayList<>();
        for (Player player : sender.getServer().getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(player)) {
                continue;
            }
            entries.add(new Entry(player.getName(), economy.balance(player)));
        }
        entries.sort(Comparator.comparingDouble(Entry::balance).reversed());

        int from = Math.min(offset, entries.size());
        return entries.subList(from, Math.min(from + size, entries.size()));
    }

    private static int onlineCount(CommandSender sender) {
        if (!(sender instanceof Player viewer)) {
            return sender.getServer().getOnlinePlayers().size();
        }
        int count = 0;
        for (Player player : sender.getServer().getOnlinePlayers()) {
            if (viewer.canSee(player)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN)) {
            return startingWith(args[0], List.of(REFRESH));
        }
        return List.of();
    }


    private record Entry(String name, double balance) {
    }
}
