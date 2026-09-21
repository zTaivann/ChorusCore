package dev.chorus.core.kits;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A kit icon chosen in game was saved, reported as saved, and came back as a chest. */
class KitIconTest {

    @Test
    void anIconSetInMemoryIsAnItemBlock() {
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("material", "DIAMOND_SWORD");

        Map<?, ?> block = KitReader.asBlock(written);
        assertNotNull(block);
        assertEquals("DIAMOND_SWORD", block.get("material"));
    }

    /** The shape that actually reaches the reader once the file has been saved and reloaded. */
    @Test
    void anIconReadBackFromTheFileIsAlsoAnItemBlock() {
        YamlConfiguration file = new YamlConfiguration();
        file.set("kit.icon.material", "DIAMOND_SWORD");
        file.set("kit.icon.name", "<gold>VIP Sword");

        Object written = file.get("kit.icon");
        assertInstanceOf(ConfigurationSection.class, written,
                "a saved config hands back a section, which is the whole point of this test");

        Map<?, ?> block = KitReader.asBlock(written);
        assertNotNull(block, "a section has to be read as a block, or the icon falls back");
        assertEquals("DIAMOND_SWORD", block.get("material"));
        assertEquals("<gold>VIP Sword", block.get("name"));
    }

    @Test
    void aBareMaterialNameIsNotABlock() {
        assertNull(KitReader.asBlock("DIAMOND_SWORD"));
        assertNull(KitReader.asBlock(null));
    }
}
