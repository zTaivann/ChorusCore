package dev.chorus.core.mail.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.mail.Mail;
import dev.chorus.core.mail.MailService;
import dev.chorus.core.players.PlayerProfiles;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /mail [read|send|sendall|clear]}: letters for players who were not here.
 *
 * <p>Reading your own inbox marks it as seen, so the reminder on login only ever counts what
 * has arrived since. Reading somebody else's leaves it unread, because staff looking into a
 * complaint should not change what the player is about to see.
 */
public final class MailCommand extends ChorusCommand {

    private static final String SEND_PERMISSION = "chorus.mail.send";
    private static final String ALL_PERMISSION = "chorus.mail.all";
    private static final String READ_OTHERS_PERMISSION = "chorus.mail.read.others";
    private static final List<String> ACTIONS = List.of("read", "send", "sendall", "clear");

    private final MailService mail;
    private final PlayerProfiles profiles;

    public MailCommand(CommandSupport support, MailService mail, PlayerProfiles profiles) {
        super(support, "mail", "chorus.mail.use");
        this.mail = mail;
        this.profiles = profiles;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "read" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "read" -> read(sender, args);
            case "send" -> send(sender, args);
            case "sendall" -> sendAll(sender, args);
            case "clear" -> clear(sender, args);
            default -> messages.send(sender, "chat.mail-usage");
        }
    }

    /** A number after {@code read} is a page; anything else is somebody's name. */
    private void read(CommandSender sender, String[] args) {
        int page = args.length > 1 ? Numbers.integer(args[1], -1) : 1;
        if (args.length > 1 && page < 0) {
            readOthers(sender, args[1]);
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        show(sender, player.getUniqueId(), Math.max(1, page), true);
    }

    private void readOthers(CommandSender sender, String name) {
        if (!sender.hasPermission(READ_OTHERS_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        profiles.find(name).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "error.player-not-found", "player", name);
                return;
            }
            messages.send(sender, "chat.mail-of", "player", found.get().name());
            show(sender, found.get().player(), 1, false);
        });
    }

    private void show(CommandSender sender, UUID owner, int page, boolean markRead) {
        mail.inbox(owner).whenComplete((letters, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (letters.isEmpty()) {
                messages.send(sender, "chat.mail-empty");
                return;
            }

            int size = mail.pageSize();
            int pages = Math.max(1, (letters.size() + size - 1) / size);
            int wanted = Math.min(Math.max(1, page), pages);
            settle(sender);

            messages.send(sender, "chat.mail-header",
                    "count", String.valueOf(letters.size()),
                    "page", String.valueOf(wanted),
                    "pages", String.valueOf(pages));

            int from = (wanted - 1) * size;
            for (Mail letter : letters.subList(from, Math.min(from + size, letters.size()))) {
                messages.send(sender, letter.read() ? "chat.mail-entry" : "chat.mail-entry-new",
                        "id", String.valueOf(letter.id()),
                        "player", letter.sender(),
                        "message", letter.body(),
                        "ago", Durations.format(System.currentTimeMillis() - letter.sentAt()));
            }
            if (markRead) {
                mail.markRead(owner);
            }
        });
    }

    private void send(CommandSender sender, String[] args) {
        if (!sender.hasPermission(SEND_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "chat.mail-usage");
            return;
        }

        OfflinePlayer target = known(sender, args[1]);
        if (target == null) {
            return;
        }
        String body = body(args, 2);
        if (body.isEmpty()) {
            messages.send(sender, "chat.mail-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        mail.send(target.getUniqueId(), senderName(sender), body).whenComplete((sent, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (!Boolean.TRUE.equals(sent)) {
                messages.send(sender, "chat.mail-full", "player", name);
                return;
            }
            settle(sender);
            messages.send(sender, "chat.mail-sent", "player", name);
            if (target instanceof Player online && online.isOnline()) {
                messages.send(online, "chat.mail-arrived", "player", senderName(sender));
            }
        });
    }

    private void sendAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ALL_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        String body = body(args, 1);
        if (body.isEmpty()) {
            messages.send(sender, "chat.mail-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        int sent = 0;
        for (Player online : sender.getServer().getOnlinePlayers()) {
            mail.send(online.getUniqueId(), senderName(sender), body);
            sent++;
        }
        settle(sender);
        messages.send(sender, "chat.mail-sent-all", "count", String.valueOf(sent));
    }

    private void clear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return;
        }

        if (args.length > 1) {
            long id = Math.max(1, Numbers.integer(args[1], -1));
            mail.clear(player.getUniqueId(), id).whenComplete((gone, failure) -> {
                if (failure != null) {
                    messages.send(player, "error.storage");
                } else if (Boolean.TRUE.equals(gone)) {
                    messages.send(player, "chat.mail-cleared-one", "id", String.valueOf(id));
                } else {
                    messages.send(player, "chat.mail-missing", "id", String.valueOf(id));
                }
            });
            return;
        }

        mail.clear(player.getUniqueId()).whenComplete((count, failure) -> {
            if (failure != null) {
                messages.send(player, "error.storage");
                return;
            }
            messages.send(player, "chat.mail-cleared", "count", String.valueOf(count));
        });
    }

    private String body(String[] args, int from) {
        String joined = String.join(" ", List.of(args).subList(from, args.length)).trim();
        return joined.length() > mail.maxLength() ? joined.substring(0, mail.maxLength()) : joined;
    }

    private static String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "Server";
    }

    /** Negative for anything that is not a number, which is how a name is told apart. */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], ACTIONS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return onlineNames(sender, args[1], false);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("read")
                && sender.hasPermission(READ_OTHERS_PERMISSION)) {
            return onlineNames(sender, args[1], true);
        }
        return List.of();
    }
}
