package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.locale.TextFormat;
import dev.chorus.core.players.PlayerProfiles;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@code /nick [player] <name|off>}: what somebody is called on screen.
 *
 * <p>Colours need their own permission, and the length limit counts letters rather than
 * codes, so a coloured nickname is not half as long as a plain one. A nickname that is
 * already somebody else's username is refused outright: two people answering to one name is
 * how a staff member ends up impersonated.
 */
public final class NickCommand extends ChorusCommand {

    private static final String OTHERS = "chorus.players.nick.others";
    private static final String COLOUR = "chorus.players.nick.colour";
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final PlayerProfiles profiles;
    private final Supplier<Integer> maxLength;

    public NickCommand(CommandSupport support, PlayerProfiles profiles, Supplier<Integer> maxLength) {
        super(support, "nick", "chorus.players.nick");
        this.profiles = profiles;
        this.maxLength = maxLength;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "players.nick-usage");
            return;
        }

        Player target;
        String wanted;
        if (args.length > 1) {
            if (!sender.hasPermission(OTHERS)) {
                messages.send(sender, "error.no-permission");
                return;
            }
            target = online(sender, args[0]);
            wanted = args[1];
        } else {
            if (!(sender instanceof Player self)) {
                messages.send(sender, "error.players-only");
                return;
            }
            target = self;
            wanted = args[0];
        }
        if (target == null) {
            return;
        }

        if (wanted.equalsIgnoreCase("off") || wanted.equalsIgnoreCase("reset")
                || wanted.equalsIgnoreCase(target.getName())) {
            if (!ready(sender)) {
                return;
            }
            profiles.nickname(target, null);
            settle(sender);
            report(sender, target, "players.nick-cleared", "players.nick-cleared-other");
            return;
        }

        if (TextFormat.hasLegacy(wanted) && !sender.hasPermission(COLOUR)) {
            messages.send(sender, "players.nick-no-colour");
            return;
        }

        String letters = PLAIN.serialize(TextFormat.parse(wanted));
        if (!ALLOWED.matcher(letters).matches()) {
            messages.send(sender, "players.nick-invalid");
            return;
        }
        if (letters.length() > maxLength.get()) {
            messages.send(sender, "players.nick-too-long",
                    "limit", String.valueOf(maxLength.get()));
            return;
        }
        if (isSomebodyElses(target, letters)
                || profiles.taken(target.getUniqueId(), wanted)) {
            messages.send(sender, "players.nick-taken", "nickname", letters);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        profiles.nickname(target, wanted);
        settle(sender);
        if (target.equals(sender)) {
            messages.send(sender, "players.nick-set", "nickname", letters);
            return;
        }
        messages.send(sender, "players.nick-set-other",
                "player", target.getName(), "nickname", letters);
        messages.send(target, "players.nick-set", "nickname", letters);
    }

    /** Somebody online whose real username this is, other than the player being renamed. */
    private static boolean isSomebodyElses(Player target, String letters) {
        Player named = target.getServer().getPlayerExact(letters);
        return named != null && !named.equals(target);
    }

    private void report(CommandSender sender, Player target, String toSelf, String toOther) {
        if (target.equals(sender)) {
            messages.send(sender, toSelf);
            return;
        }
        messages.send(sender, toOther, "player", target.getName());
        messages.send(target, toSelf);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("off"));
            if (sender.hasPermission(OTHERS)) {
                options.addAll(onlineNames(sender, args[0], true));
            }
            return startingWith(args[0].toLowerCase(Locale.ROOT), options);
        }
        return args.length == 2 ? startingWith(args[1], List.of("off")) : List.of();
    }
}
