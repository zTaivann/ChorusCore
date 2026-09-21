package dev.chorus.core.items.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /recipe <item|hand> [number]}: how something is made. */
public final class RecipeCommand extends ChorusCommand {

    private static final int GRID = 3;
    private static final String EMPTY = "-";

    public RecipeCommand(CommandSupport support) {
        super(support, "recipe", "chorus.items.recipe");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Material material = wanted(sender, args);
        if (material == null) {
            return;
        }

        List<Recipe> recipes = sender.getServer().getRecipesFor(new ItemStack(material));
        String name = material.name().toLowerCase(Locale.ROOT);
        if (recipes.isEmpty()) {
            messages.send(sender, "items.recipe-none", "item", name);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        int which = args.length > 1 ? Numbers.integer(args[1], -1) : 1;
        if (which < 1 || which > recipes.size()) {
            messages.send(sender, "items.recipe-unknown",
                    "number", String.valueOf(which), "count", String.valueOf(recipes.size()));
            return;
        }

        settle(sender);
        Recipe recipe = recipes.get(which - 1);
        messages.send(sender, "items.recipe-header",
                "item", name,
                "number", String.valueOf(which),
                "count", String.valueOf(recipes.size()));
        show(sender, recipe);
        if (recipes.size() > which) {
            messages.send(sender, "items.recipe-more",
                    "item", name, "number", String.valueOf(which + 1));
        }
    }

    private void show(CommandSender sender, Recipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < GRID; row++) {
                String line = row < shape.length ? shape[row] : "";
                messages.send(sender, "items.recipe-row",
                        "one", ingredient(line, 0, choices),
                        "two", ingredient(line, 1, choices),
                        "three", ingredient(line, 2, choices));
            }
            return;
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            List<String> parts = new ArrayList<>();
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                parts.add(describe(choice));
            }
            messages.send(sender, "items.recipe-shapeless", "items", String.join(", ", parts));
            return;
        }
        messages.send(sender, "items.recipe-other",
                "kind", recipe.getClass().getSimpleName()
                        .replace("Recipe", "").toLowerCase(Locale.ROOT));
    }

    private static String ingredient(String row, int column, Map<Character, RecipeChoice> choices) {
        if (column >= row.length()) {
            return EMPTY;
        }
        char key = row.charAt(column);
        RecipeChoice choice = choices.get(key);
        return choice == null ? EMPTY : describe(choice);
    }

    /** The first thing a slot accepts, since a whole tag would not fit on a line. */
    private static String describe(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials
                && !materials.getChoices().isEmpty()) {
            return materials.getChoices().get(0).name().toLowerCase(Locale.ROOT);
        }
        ItemStack item = choice.getItemStack();
        return item == null ? EMPTY : item.getType().name().toLowerCase(Locale.ROOT);
    }

    private @Nullable Material wanted(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "items.recipe-usage");
                return null;
            }
            Material held = player.getInventory().getItemInMainHand().getType();
            if (held.isAir()) {
                messages.send(sender, "items.itemdb-empty");
                return null;
            }
            return held;
        }

        Material material = Material.matchMaterial(args[0]);
        if (material == null) {
            messages.send(sender, "items.itemdb-unknown", "item", args[0]);
            return null;
        }
        return material;
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("hand"));
        String typed = args[0].toLowerCase(Locale.ROOT);
        if (typed.length() >= 2) {
            for (Material material : Material.values()) {
                if (options.size() > 20) {
                    break;
                }
                if (material.isItem() && !material.isLegacy()
                        && material.name().toLowerCase(Locale.ROOT).startsWith(typed)) {
                    options.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return startingWith(args[0], options);
    }
}
