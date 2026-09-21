package dev.chorus.core.kits.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.kits.Kit;
import dev.chorus.core.kits.KitEditor;
import dev.chorus.core.kits.KitService;
import dev.chorus.core.kits.menu.KitEditMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Builds and changes kits from inside the game, by screen or by command. */
public final class KitEditCommand extends PlayerCommand {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Each setting with the shape of its value and the line that explains it. */
    private record Setting(String name, String value, String help) {
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("create", "", "kits.edit-help-create"),
            new Setting("items", "", "kits.edit-help-items"),
            new Setting("icon", "<item>", "kits.edit-help-icon"),
            new Setting("display", "<text>", "kits.edit-help-display"),
            new Setting("lore", "<text>", "kits.edit-help-lore"),
            new Setting("cooldown", "<seconds>", "kits.edit-help-cooldown"),
            new Setting("maxclaims", "<number>", "kits.edit-help-maxclaims"),
            new Setting("price", "<amount>", "kits.edit-help-price"),
            new Setting("permission", "<node>", "kits.edit-help-permission"),
            new Setting("onetime", "<true|false>", "kits.edit-help-onetime"),
            new Setting("autoarmor", "<true|false>", "kits.edit-help-autoarmor"),
            new Setting("clearinventory", "<true|false>", "kits.edit-help-clearinventory"),
            new Setting("placeholders", "<true|false>", "kits.edit-help-placeholders"),
            new Setting("delete", "", "kits.edit-help-delete"));

    private final KitService kits;
    private final KitEditor editor;
    private final KitEditMenu menu;
    private final Economy economy;

    public KitEditCommand(CommandSupport support, KitService kits, KitEditor editor,
                          KitEditMenu menu, Economy economy) {
        super(support, "kitedit", "chorus.kits.edit");
        this.kits = kits;
        this.editor = editor;
        this.menu = menu;
        this.economy = economy;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            menu.openList(player);
            return;
        }
        if (args[0].equalsIgnoreCase("help")) {
            help(player);
            return;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        Kit kit = kits.find(name).orElse(null);

        if (args.length == 1) {
            if (kit == null) {
                messages.send(player, "kits.edit-unknown", "kit", name);
                return;
            }
            menu.open(player, name);
            return;
        }

        String setting = args[1].toLowerCase(Locale.ROOT);
        if (setting.equals("info")) {
            if (kit == null) {
                messages.send(player, "kits.edit-unknown", "kit", name);
                return;
            }
            describe(player, kit);
            return;
        }
        if (SETTINGS.stream().noneMatch(entry -> entry.name().equals(setting))) {
            messages.send(player, "kits.edit-unknown-setting", "setting", args[1]);
            help(player);
            return;
        }

        if (setting.equals("create")) {
            create(player, name, kit != null);
            return;
        }
        if (kit == null) {
            messages.send(player, "kits.edit-unknown", "kit", name);
            return;
        }
        if (!ready(player)) {
            return;
        }

        String value = args.length > 2
                ? String.join(" ", List.of(args).subList(2, args.length)).trim()
                : "";
        switch (setting) {
            case "delete" -> delete(player, name);
            case "items" -> items(player, name);
            default -> change(player, name, setting, value);
        }
    }

    /** One clickable line per setting, rather than a wall of pipes nobody can read in chat. */
    private void help(Player player) {
        messages.send(player, "kits.edit-help-header");
        for (Setting entry : SETTINGS) {
            String usage = ("/kitedit <kit> " + entry.name() + " " + entry.value()).trim();
            Component line = messages.render("kits.edit-help-line",
                    "usage", usage,
                    "what", messages.plain(entry.help()));
            player.sendMessage(line
                    .clickEvent(ClickEvent.suggestCommand("/kitedit "))
                    .hoverEvent(HoverEvent.showText(messages.render("kits.edit-help-hover"))));
        }
        messages.send(player, "kits.edit-help-footer");
    }

    private void describe(Player player, Kit kit) {
        messages.send(player, "kits.edit-info-header", "kit", kit.name());
        messages.send(player, "kits.edit-info-display", "display", PLAIN.serialize(kit.display()));
        messages.send(player, "kits.edit-info-icon",
                "icon", kit.icon().getType().name().toLowerCase(Locale.ROOT));
        messages.send(player, "kits.edit-info-cooldown", "cooldown", cooldownOf(kit));
        messages.send(player, "kits.edit-info-maxclaims", "claims", claimsOf(kit));
        messages.send(player, "kits.edit-info-price", "price", priceOf(kit));
        messages.send(player, "kits.edit-info-permission", "permission",
                kit.permission().isEmpty() ? messages.plain("kits.word-everyone") : kit.permission());
        messages.send(player, "kits.edit-info-onetime", "onetime", word(kit.oneTime()));
        messages.send(player, "kits.edit-info-autoarmor", "autoarmor", word(kit.autoArmor()));
        messages.send(player, "kits.edit-info-clearinventory",
                "clearinventory", word(kit.clearInventory()));
        messages.send(player, "kits.edit-info-placeholders",
                "placeholders", word(kit.placeholders()));
        messages.send(player, "kits.edit-info-items", "count", String.valueOf(kit.items().size()));
        if (!kit.requirements().isEmpty()) {
            messages.send(player, "kits.edit-info-requirements",
                    "count", String.valueOf(kit.requirements().size()));
        }
        if (!kit.claimActions().isEmpty() || !kit.failActions().isEmpty()) {
            messages.send(player, "kits.edit-info-actions",
                    "claim", String.valueOf(kit.claimActions().size()),
                    "fail", String.valueOf(kit.failActions().size()));
        }
        messages.send(player, "kits.edit-info-footer", "kit", kit.name());
    }

    private String cooldownOf(Kit kit) {
        if (kit.oneTime()) {
            return messages.plain("kits.word-once");
        }
        return kit.cooldownSeconds() == 0
                ? messages.plain("kits.word-none")
                : Durations.format(TimeUnit.SECONDS.toMillis(kit.cooldownSeconds()));
    }

    private String claimsOf(Kit kit) {
        return kit.maxClaims() == 0 ? messages.plain("kits.word-unlimited")
                : String.valueOf(kit.maxClaims());
    }

    private String priceOf(Kit kit) {
        return kit.price() <= 0 ? messages.plain("kits.word-free") : economy.format(kit.price());
    }

    private String word(boolean value) {
        return messages.plain(value ? "kits.word-true" : "kits.word-false");
    }

    private void create(Player player, String name, boolean exists) {
        if (exists) {
            messages.send(player, "kits.edit-exists", "kit", name);
            return;
        }
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            messages.send(player, "kits.edit-bad-name");
            return;
        }
        if (!ready(player)) {
            return;
        }
        if (!editor.create(name, player)) {
            messages.send(player, "error.storage");
            return;
        }
        settle(player);
        messages.send(player, "kits.edit-created", "kit", name);
        menu.open(player, name);
    }

    private void delete(Player player, String name) {
        if (!editor.delete(name)) {
            messages.send(player, "error.storage");
            return;
        }
        settle(player);
        messages.send(player, "kits.edit-deleted", "kit", name);
    }

    private void items(Player player, String name) {
        KitEditor.Result result = editor.setItems(name, player);
        if (!result.saved()) {
            messages.send(player, "error.storage");
            return;
        }
        settle(player);
        messages.send(player, "kits.edit-items", "kit", name, "count", String.valueOf(result.count()));
        if (result.simplified() > 0) {
            messages.send(player, "kits.edit-items-simplified",
                    "count", String.valueOf(result.simplified()));
        }
    }

    private void change(Player player, String name, String setting, String value) {
        if (setting.equals("icon") && !value.isEmpty()) {
            Material icon = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
            if (icon == null || !icon.isItem()) {
                messages.send(player, "kits.edit-bad-icon", "value", value);
                return;
            }
            value = icon.name();
        }
        if (isNumeric(setting) && !value.isEmpty() && !isNumber(value)) {
            messages.send(player, "kits.edit-bad-number", "setting", setting, "value", value);
            return;
        }

        if (!editor.set(name, setting, value)) {
            messages.send(player, "error.storage");
            return;
        }
        settle(player);
        if (value.isEmpty()) {
            messages.send(player, "kits.edit-cleared", "kit", name, "setting", setting);
            return;
        }

        // Shown as it will be read back, not as it was typed.
        messages.send(player, "kits.edit-set",
                "kit", name, "setting", setting, "value", readable(setting, value));
    }

    private String readable(String setting, String value) {
        return switch (setting) {
            case "cooldown" -> Durations.format(TimeUnit.SECONDS.toMillis((long) number(value)));
            case "maxclaims" -> (long) number(value) + " " + messages.plain("kits.word-claims");
            case "price" -> economy.format(number(value));
            case "onetime", "autoarmor", "clearinventory", "placeholders" ->
                    word(Boolean.parseBoolean(value));
            default -> value;
        };
    }

    private static boolean isNumeric(String setting) {
        return setting.equals("cooldown") || setting.equals("maxclaims") || setting.equals("price");
    }

    private static double number(String value) {
        return Double.parseDouble(value.replace(',', '.'));
    }

    private static boolean isNumber(String value) {
        try {
            return number(value) >= 0;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(kits.all().stream().map(Kit::name).toList());
            names.add("help");
            return startingWith(args[0], names);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>(SETTINGS.stream().map(Setting::name).toList());
            names.add("info");
            return startingWith(args[1], names);
        }
        if (args.length != 3) {
            return List.of();
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "onetime", "autoarmor", "clearinventory", "placeholders" ->
                    startingWith(args[2], List.of("true", "false"));
            case "cooldown" -> startingWith(args[2], List.of("0", "60", "3600", "86400"));
            case "maxclaims" -> startingWith(args[2], List.of("0", "1", "5", "10"));
            default -> List.of();
        };
    }
}
