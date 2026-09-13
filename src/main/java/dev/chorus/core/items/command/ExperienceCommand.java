package dev.chorus.core.items.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /exp [show|give|take|set|reset] [player] <amount>}: experience, in points or in
 * levels.
 *
 * <p>An amount ending in {@code L} is levels; anything else is points. Setting a total is
 * done by emptying the bar first, because Bukkit's level and progress are two numbers and
 * writing one without the other leaves a player on a level they have not earned.
 */
public final class ExperienceCommand extends ChorusCommand {

    private static final String OTHERS = "chorus.items.exp.others";
    private static final List<String> ACTIONS = List.of("show", "give", "take", "set", "reset");

    public ExperienceCommand(CommandSupport support) {
        super(support, "exp", "chorus.items.exp");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "show" : args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            messages.send(sender, "items.exp-usage");
            return;
        }

        boolean needsAmount = action.equals("give") || action.equals("take") || action.equals("set");
        int nameAt = needsAmount && args.length > 2 ? 1 : -1;
        Player target = target(sender, args, nameAt);
        if (target == null) {
            return;
        }

        if (action.equals("show")) {
            show(sender, target);
            return;
        }
        if (action.equals("reset")) {
            if (!ready(sender)) {
                return;
            }
            setTotal(target, 0);
            settle(sender);
            report(sender, target, "items.exp-reset", "items.exp-reset-other");
            return;
        }

        String rawAmount = args[args.length - 1];
        boolean levels = rawAmount.toLowerCase(Locale.ROOT).endsWith("l");
        int amount = amount(rawAmount);
        if (amount < 0 || args.length < 2) {
            messages.send(sender, "items.exp-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        onPlayer(target, () -> {
            switch (action) {
                case "give" -> {
                    if (levels) {
                        target.giveExpLevels(amount);
                    } else {
                        target.giveExp(amount);
                    }
                }
                case "take" -> {
                    if (levels) {
                        target.giveExpLevels(-amount);
                    } else {
                        setTotal(target, Math.max(0, total(target) - amount));
                    }
                }
                default -> {
                    if (levels) {
                        setTotal(target, 0);
                        target.giveExpLevels(amount);
                    } else {
                        setTotal(target, amount);
                    }
                }
            }
        });

        settle(sender);
        String written = amount + (levels ? " levels" : " points");
        if (target.equals(sender)) {
            messages.send(sender, "items.exp-changed", "amount", written);
        } else {
            messages.send(sender, "items.exp-changed-other",
                    "amount", written, "player", target.getName());
            messages.send(target, "items.exp-changed", "amount", written);
        }
    }

    private void show(CommandSender sender, Player target) {
        String key = target.equals(sender) ? "items.exp-show" : "items.exp-show-other";
        messages.send(sender, key,
                "player", target.getName(),
                "level", String.valueOf(target.getLevel()),
                "points", String.valueOf(total(target)));
    }

    private void report(CommandSender sender, Player target, String toSelf, String toOther) {
        if (target.equals(sender)) {
            messages.send(sender, toSelf);
            return;
        }
        messages.send(sender, toOther, "player", target.getName());
        messages.send(target, toSelf);
    }

    /** The named player when one was given and allowed, otherwise the sender. */
    private Player target(CommandSender sender, String[] args, int nameAt) {
        if (nameAt > 0 && nameAt < args.length) {
            if (!sender.hasPermission(OTHERS)) {
                messages.send(sender, "error.no-permission");
                return null;
            }
            return online(sender, args[nameAt]);
        }
        if (args.length == 2 && !isAmount(args[1])) {
            if (!sender.hasPermission(OTHERS)) {
                messages.send(sender, "error.no-permission");
                return null;
            }
            return online(sender, args[1]);
        }
        if (sender instanceof Player self) {
            return self;
        }
        messages.send(sender, "error.players-only");
        return null;
    }

    /** Both halves at once, so a level is never set without the progress that goes with it. */
    private static void setTotal(Player player, int points) {
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        if (points > 0) {
            player.giveExp(points);
        }
    }

    /**
     * What the bar is really worth.
     *
     * <p>{@code getTotalExperience} is the number Minecraft has collected, not the number a
     * player is carrying: spending levels at an anvil leaves it alone. Counting the levels
     * back up is the only way to a figure that matches the bar.
     */
    private static int total(Player player) {
        int level = player.getLevel();
        int points = Math.round(player.getExp() * pointsToNext(level));
        for (int reached = 0; reached < level; reached++) {
            points += pointsToNext(reached);
        }
        return points;
    }

    /** The vanilla curve: flat to sixteen, steeper to thirty-one, steeper again after. */
    private static int pointsToNext(int level) {
        if (level < 16) {
            return 2 * level + 7;
        }
        return level < 31 ? 5 * level - 38 : 9 * level - 158;
    }

    private static boolean isAmount(String raw) {
        return amount(raw) >= 0;
    }

    private static int amount(String raw) {
        String digits = raw.toLowerCase(Locale.ROOT);
        if (digits.endsWith("l")) {
            digits = digits.substring(0, digits.length() - 1);
        }
        try {
            int value = Integer.parseInt(digits);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], ACTIONS);
        }
        if (args.length == 2 && sender.hasPermission(OTHERS)) {
            return onlineNames(sender, args[1], true);
        }
        return List.of();
    }
}
