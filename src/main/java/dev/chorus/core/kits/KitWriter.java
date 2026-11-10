package dev.chorus.core.kits;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns real items back into the shape {@link KitReader} reads. */
final class KitWriter {

    /** The key holding the exact item. */
    static final String DATA = "data";

    private KitWriter() {
    }

    /** @return the items in config form, skipping the empty slots. */
    static List<Map<String, Object>> describe(ItemStack[] contents) {
        List<Map<String, Object>> written = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            written.add(describe(item));
        }
        return written;
    }

    /**
     * One item on its own, as an icon is.
     *
     * <p>Stored exactly as the server's versioned serialized form, so every component survives
     * updates. {@code material} and {@code amount} are written beside it only for readability.
     */
    static Map<String, Object> describe(ItemStack item) {
        // Keeps a fixed key order.
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("material", item.getType().name());
        if (item.getAmount() != 1) {
            written.put("amount", item.getAmount());
        }
        written.put(DATA, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return written;
    }
}
