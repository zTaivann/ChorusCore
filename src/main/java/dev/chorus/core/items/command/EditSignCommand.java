package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.items.SignClipboard;
import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** {@code /editsign <set|clear|copy|paste>}: rewrites the sign you are looking at. */
public final class EditSignCommand extends PlayerCommand {

    private static final int RANGE = 8;
    private static final int LINES = 4;
    private static final int MAX_LENGTH = 45;
    private static final List<String> ACTIONS = List.of("set", "clear", "copy", "paste");

    private final SignClipboard clipboard;
    private final Predicate<Block> reserved;

    /**
     * @param reserved says whether a block is a sign the plugin itself is using
     */
    public EditSignCommand(CommandSupport support, SignClipboard clipboard,
                           Predicate<Block> reserved) {
        super(support, "editsign", "chorus.items.editsign");
        this.clipboard = clipboard;
        this.reserved = reserved;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "items.sign-usage");
            return;
        }

        Block block = player.getTargetBlockExact(RANGE);
        if (block == null || !(block.getState() instanceof Sign sign)) {
            messages.send(player, "items.sign-none");
            return;
        }
        if (reserved.test(block)) {
            messages.send(player, "items.sign-reserved");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(player, sign, args);
            case "clear" -> clear(player, sign, args);
            case "copy" -> copy(player, sign);
            case "paste" -> paste(player, sign);
            default -> messages.send(player, "items.sign-usage");
        }
    }

    private void set(Player player, Sign sign, String[] args) {
        if (args.length < 3) {
            messages.send(player, "items.sign-usage");
            return;
        }
        int line = line(args[1]);
        if (line < 0) {
            messages.send(player, "items.sign-line-range", "lines", String.valueOf(LINES));
            return;
        }
        if (!ready(player)) {
            return;
        }

        String text = String.join(" ", List.of(args).subList(2, args.length));
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        sign.line(line, TextFormat.parse(text));
        sign.update(true, false);

        settle(player);
        messages.send(player, "items.sign-set", "line", String.valueOf(line + 1));
    }

    private void clear(Player player, Sign sign, String[] args) {
        if (!ready(player)) {
            return;
        }

        if (args.length > 1) {
            int line = line(args[1]);
            if (line < 0) {
                messages.send(player, "items.sign-line-range", "lines", String.valueOf(LINES));
                return;
            }
            sign.line(line, Component.empty());
        } else {
            for (int line = 0; line < LINES; line++) {
                sign.line(line, Component.empty());
            }
        }
        sign.update(true, false);

        settle(player);
        messages.send(player, "items.sign-cleared");
    }

    private void copy(Player player, Sign sign) {
        if (!ready(player)) {
            return;
        }
        List<Component> lines = new ArrayList<>(LINES);
        for (int line = 0; line < LINES; line++) {
            lines.add(sign.line(line));
        }
        clipboard.put(player, lines);

        settle(player);
        messages.send(player, "items.sign-copied");
    }

    private void paste(Player player, Sign sign) {
        List<Component> lines = clipboard.of(player);
        if (lines == null) {
            messages.send(player, "items.sign-nothing-copied");
            return;
        }
        if (!ready(player)) {
            return;
        }

        for (int line = 0; line < LINES; line++) {
            sign.line(line, lines.get(line));
        }
        sign.update(true, false);

        settle(player);
        messages.send(player, "items.sign-pasted");
    }

    /** Counted from one on screen, from zero in the block. -1 for anything else. */
    private static int line(String raw) {
        try {
            int typed = Integer.parseInt(raw);
            return typed < 1 || typed > LINES ? -1 : typed - 1;
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("clear"))) {
            return startingWith(args[1], List.of("1", "2", "3", "4"));
        }
        return List.of();
    }
}
