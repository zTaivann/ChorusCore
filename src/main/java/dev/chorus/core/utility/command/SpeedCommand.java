package dev.chorus.core.utility.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /speed [walk|fly] <1-10> [player]}.
 *
 * <p>The number is scaled so that 1 is exactly what vanilla gives you and 10 is the fastest
 * the client accepts, which makes the same figure mean the same thing on foot and in the air.
 */
public final class SpeedCommand extends ChorusCommand {

    private static final String OTHERS_PERMISSION = "chorus.utility.speed.others";

    private static final float VANILLA_WALK = 0.2f;
    private static final float VANILLA_FLY = 0.1f;
    private static final float FASTEST = 1.0f;

    private final UtilityService utility;

    public SpeedCommand(CommandSupport support, UtilityService utility) {
        super(support, "speed", "chorus.utility.speed");
        this.utility = utility;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "utility.speed-usage");
            return;
        }

        int next = 0;
        Boolean flying = mode(args[0]);
        if (flying != null) {
            next = 1;
        }
        if (args.length <= next) {
            messages.send(sender, "utility.speed-usage");
            return;
        }

        double amount = parse(args[next]);
        double maximum = utility.settings().speed().maximum();
        if (amount < 0 || amount > maximum) {
            messages.send(sender, "utility.speed-range", "max", trim(maximum));
            return;
        }

        Player target = resolve(sender, args, next + 1);
        if (target == null) {
            return;
        }
        if (flying == null) {
            // No mode given, so follow whichever one they are actually using.
            flying = target.isFlying() || target.getAllowFlight();
        }
        if (!ready(sender)) {
            return;
        }

        float scaled = scale(amount, maximum, flying ? VANILLA_FLY : VANILLA_WALK);
        if (flying) {
            target.setFlySpeed(scaled);
        } else {
            target.setWalkSpeed(scaled);
        }

        settle(sender);
        String mode = flying ? "fly" : "walk";
        if (target.equals(sender)) {
            messages.send(sender, "utility.speed-set", "mode", mode, "amount", trim(amount));
        } else {
            messages.send(sender, "utility.speed-set-other",
                    "player", target.getName(), "mode", mode, "amount", trim(amount));
            messages.send(target, "utility.speed-received",
                    "player", sender.getName(), "mode", mode, "amount", trim(amount));
        }
    }

    /** Returns null when the word is not a mode, which means it must be the number. */
    private static Boolean mode(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.equals("fly") || lower.equals("flight")) {
            return Boolean.TRUE;
        }
        if (lower.equals("walk") || lower.equals("run")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private Player resolve(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            if (sender instanceof Player self) {
                return self;
            }
            messages.send(sender, "error.players-only");
            return null;
        }
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return null;
        }

        Player target = sender.getServer().getPlayerExact(args[index]);
        if (target == null || (sender instanceof Player viewer && !viewer.canSee(target))) {
            messages.send(sender, "error.player-not-found", "player", args[index]);
            return null;
        }
        return target;
    }

    /** 0 stops you dead, 1 is vanilla, and the configured maximum is as fast as it goes. */
    private static float scale(double amount, double maximum, float vanilla) {
        if (amount <= 0) {
            return 0;
        }
        if (amount <= 1 || maximum <= 1) {
            return vanilla;
        }
        double share = (amount - 1) / (maximum - 1);
        return (float) Math.min(FASTEST, vanilla + share * (FASTEST - vanilla));
    }

    private static double parse(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], List.of("walk", "fly", "1", "2", "5", "10"));
        }
        if (args.length == 2 && mode(args[0]) != null) {
            return startingWith(args[1], List.of("1", "2", "5", "10"));
        }
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }

        return onlineNames(sender, args[args.length - 1], true);
    }
}
