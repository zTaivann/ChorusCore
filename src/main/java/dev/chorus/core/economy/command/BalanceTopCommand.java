package dev.chorus.core.economy.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.economy.Balances;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.EconomyService;
import dev.chorus.core.economy.EconomySetup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The richest players on the server. */
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
        List<Entry> shown = ledger == null ? online(sender) : ranked();
        if (service.settings().topHideEmpty()) {
            shown = shown.stream().filter(entry -> entry.balance() > 0).toList();
        }
        int total = shown.size();
        int pages = Math.max(1, (total + size - 1) / size);
        int page = Math.min(pages, Math.max(1, args.length > 0 ? Numbers.integer(args[0], 1) : 1));
        int from = Math.min((page - 1) * size, total);
        List<Entry> entries = shown.subList(from, Math.min(from + size, total));

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

    /** Throws the ranking away. */
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

    private List<Entry> ranked() {
        List<Balances.Ranked> ranking = ledger.ranking();
        List<Entry> entries = new ArrayList<>(ranking.size());
        for (Balances.Ranked account : ranking) {
            entries.add(new Entry(account.name(), account.balance()));
        }
        return entries;
    }

    /** With another plugin keeping the balances, only the players online can be ranked. */
    private List<Entry> online(CommandSender sender) {
        List<Entry> entries = new ArrayList<>();
        for (Player player : sender.getServer().getOnlinePlayers()) {
            if ((sender instanceof Player viewer && !viewer.canSee(player))
                    || player.hasPermission(EconomySetup.BALTOP_HIDDEN)) {
                continue;
            }
            entries.add(new Entry(player.getName(), economy.balance(player)));
        }
        entries.sort(Comparator.comparingDouble(Entry::balance).reversed());
        return entries;
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
