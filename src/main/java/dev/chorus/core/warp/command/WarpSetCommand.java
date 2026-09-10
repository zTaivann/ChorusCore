package dev.chorus.core.warp.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.warp.WarpDetails;
import dev.chorus.core.warp.WarpDetailsService;
import dev.chorus.core.warp.WarpService;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /warpset <warp> <setting> [value]}: everything about a warp except where it is.
 *
 * <p>Leaving the value off puts a setting back to what the {@code /warp} command block says,
 * which is how a warp is un-priced or unlocked again.
 */
public final class WarpSetCommand extends ChorusCommand {

    /**
     * Each setting with the shape of its value and the line that explains it. The message
     * key is written out rather than built from the name, so the check that every line in
     * messages.yml is reachable can see all six.
     */
    private record Setting(String name, String value, String help) {
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("icon", "<item>", "warp.settings-help-icon"),
            new Setting("permission", "<node>", "warp.settings-help-permission"),
            new Setting("price", "<amount>", "warp.settings-help-price"),
            new Setting("cooldown", "<seconds>", "warp.settings-help-cooldown"),
            new Setting("description", "<text>", "warp.settings-help-description"),
            new Setting("section", "<name>", "warp.settings-help-section"));

    /** One clickable line per setting, rather than a wall of pipes nobody can read in chat. */
    private void help(CommandSender sender) {
        messages.send(sender, "warp.settings-help-header");
        for (Setting entry : SETTINGS) {
            String usage = "/warpset <warp> " + entry.name() + " " + entry.value();
            sender.sendMessage(messages.render("warp.settings-help-line",
                            "usage", usage,
                            "what", messages.plain(entry.help()))
                    .clickEvent(ClickEvent.suggestCommand("/warpset "))
                    .hoverEvent(HoverEvent.showText(messages.render("warp.settings-help-hover"))));
        }
        messages.send(sender, "warp.settings-help-footer");
    }
    private final WarpService warps;
    private final WarpDetailsService details;

    public WarpSetCommand(CommandSupport support, WarpService warps, WarpDetailsService details) {
        super(support, "warpset", "chorus.warp.set");
        this.warps = warps;
        this.details = details;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }

        String key = Names.normalise(args[0]);
        if (warps.find(key).isEmpty()) {
            messages.send(sender, "warp.unknown", "warp", key);
            return;
        }

        String setting = args[1].toLowerCase(Locale.ROOT);
        if (SETTINGS.stream().noneMatch(entry -> entry.name().equals(setting))) {
            help(sender);
            return;
        }

        String value = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
                : "";
        boolean clearing = value.isEmpty();

        WarpDetails current = details.of(key);
        WarpDetails updated = switch (setting) {
            case "icon" -> icon(sender, current, value, clearing);
            case "permission" -> current.withPermission(clearing ? null : value);
            case "price" -> price(sender, current, value, clearing);
            case "cooldown" -> cooldown(sender, current, value, clearing);
            case "description" -> current.withDescription(clearing ? null : value);
            default -> current.withSection(clearing ? null : Names.normalise(value));
        };
        if (updated == null) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        details.save(updated);
        settle(sender);
        messages.send(sender, clearing ? "warp.settings-cleared" : "warp.settings-done",
                "warp", key, "setting", setting, "value", value);
    }

    private WarpDetails icon(CommandSender sender, WarpDetails current, String value, boolean clearing) {
        if (clearing) {
            return current.withIcon(null);
        }
        Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            messages.send(sender, "warp.settings-bad-icon", "value", value);
            return null;
        }
        return current.withIcon(material.name());
    }

    private WarpDetails price(CommandSender sender, WarpDetails current, String value, boolean clearing) {
        if (clearing) {
            return current.withPrice(WarpDetails.INHERIT_PRICE);
        }
        double amount;
        try {
            amount = Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            amount = -1;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            messages.send(sender, "warp.settings-bad-number", "value", value);
            return null;
        }
        return current.withPrice(Math.round(amount * 100.0) / 100.0);
    }

    private WarpDetails cooldown(CommandSender sender, WarpDetails current, String value, boolean clearing) {
        if (clearing) {
            return current.withCooldown(WarpDetails.INHERIT_COOLDOWN);
        }
        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            seconds = -1;
        }
        if (seconds < 0) {
            messages.send(sender, "warp.settings-bad-number", "value", value);
            return null;
        }
        return current.withCooldown(seconds);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], warps.all().stream().map(NamedLocation::name).toList());
        }
        return args.length == 2
                ? startingWith(args[1], SETTINGS.stream().map(Setting::name).toList())
                : List.of();
    }
}
