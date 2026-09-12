package dev.chorus.core.players.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.players.AfkService;
import org.bukkit.entity.Player;

/** {@code /afk [reason]}: marks somebody away, with a word about why if they want one. */
public final class AfkCommand extends PlayerCommand {

    private static final int MAX_REASON = 64;

    private final AfkService afk;

    public AfkCommand(CommandSupport support, AfkService afk) {
        super(support, "afk", "chorus.players.afk");
        this.afk = afk;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        afk.toggle(player, reason(args));
        settle(player);
    }

    /** Plain text: a reason goes out to everybody, so it carries no colours of its own. */
    private static String reason(String[] args) {
        if (args.length == 0) {
            return "";
        }
        String joined = String.join(" ", args).replaceAll("[&§<>]", "").trim();
        return joined.length() > MAX_REASON ? joined.substring(0, MAX_REASON) : joined;
    }
}
