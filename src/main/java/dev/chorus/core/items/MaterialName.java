package dev.chorus.core.items;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A material next to the name players type for it. */
public record MaterialName(Material material, String name) {

    /** Every material in {@link Material#values()} order, lower-cased once. */
    public static final List<MaterialName> ALL = Arrays.stream(Material.values())
            .map(material -> new MaterialName(material, material.name().toLowerCase(Locale.ROOT)))
            .toList();
}
