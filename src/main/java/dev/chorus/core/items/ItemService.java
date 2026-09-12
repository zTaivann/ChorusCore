package dev.chorus.core.items;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
     * Turns typed text into a component. Only a player holding the format permission gets
     * their formatting read; for everyone else the text stays exactly as they wrote it.
     *
     * <p>Both ways of writing it work, and work together: {@code &c&lRed} and
     * {@code <red><bold>Red} name the same sword, and a line may use either.
     *
     * <p>Names and lore are also given a plain style, because Minecraft renders anything an
     * item is named in italics by default and nobody ever wants that.
     */
    public Component text(Player author, String raw) {
        Component text = author.hasPermission(FORMAT_PERMISSION)
                ? TextFormat.parse(raw)
                : Component.text(raw);

        // Wrapping rather than setting it on the text itself: children inherit the parent's
        // style only where they have not chosen one, so somebody who really did ask for
        // italics still gets them.
        return Component.text()
                .decoration(TextDecoration.ITALIC, false)
                .append(text)
                .build();
    }
}
