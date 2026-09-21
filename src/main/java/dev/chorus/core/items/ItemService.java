package dev.chorus.core.items;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class ItemService {

    private static final String FORMAT_PERMISSION = "chorus.items.format";

    private volatile ItemSettings settings;

    ItemService(ItemSettings settings) {
        this.settings = settings;
    }

    public ItemSettings settings() {
        return settings;
    }

    void apply(ItemSettings updated) {
        this.settings = updated;
    }

    /**
     * A typed name. Only a player holding the format permission gets their formatting read;
     * for everyone else the text stays exactly as they wrote it.
     */
    public Component name(Player author, String raw) {
        return TextFormat.upright(typed(author, raw));
    }

    /** A typed line of lore, drawn grey rather than the purple the game falls back to. */
    public Component lore(Player author, String raw) {
        return TextFormat.asLore(typed(author, raw));
    }

    private Component typed(Player author, String raw) {
        return author.hasPermission(FORMAT_PERMISSION)
                ? TextFormat.parse(raw)
                : Component.text(raw);
    }
}
