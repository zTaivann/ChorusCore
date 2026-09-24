package dev.chorus.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holds permissions.yml to the nodes the code actually checks. */
class PermissionsTest {

    private static final String FILE = "permissions.yml";

    /** The group holding the nodes there is one of per home, kit, warp or world. */
    private static final String FAMILIES = "families";

    private static final Pattern NODE = Pattern.compile("\"(chorus\\.[a-z0-9_.]*)\"");

    /** Nodes built from a name the plugin already knows, which the scanner sees only as a prefix. */
    private static final Set<String> BUILT = Set.of(
            "chorus.utility.motd", "chorus.utility.info",
            "chorus.utility.invsee", "chorus.utility.invsee.edit",
            "chorus.utility.ecsee", "chorus.utility.ecsee.edit",
            "chorus.utility.craft", "chorus.utility.anvil", "chorus.utility.smithingtable",
            "chorus.utility.grindstone", "chorus.utility.stonecutter", "chorus.utility.loom",
            "chorus.utility.cartography", "chorus.utility.enchanting",
            "chorus.utility.enderchest");

    @Test
    void everyNodeTheCodeChecksIsListed() throws IOException {
        Set<String> listed = listed();
        List<String> missing = new ArrayList<>();

        for (String node : inSources()) {
            if (node.endsWith(".")) {
                if (listed.stream().noneMatch(one -> one.startsWith(node))) {
                    missing.add(node + "<...>");
                }
            } else if (!listed.contains(node)) {
                missing.add(node);
            }
        }
        assertEquals(List.of(), missing, FILE + " does not list these nodes");
    }

    @Test
    void everyNodeListedIsOneTheCodeChecks() throws IOException {
        Set<String> inSources = inSources();
        List<String> unknown = listed().stream()
                .map(node -> node.replaceAll("<[a-z]+>$", ""))
                .filter(node -> !inSources.contains(node) && !BUILT.contains(node))
                .filter(node -> inSources.stream().noneMatch(
                        known -> known.endsWith(".") && node.startsWith(known)))
                .toList();

        assertEquals(List.of(), unknown, FILE + " lists nodes nothing checks");
    }

    @Test
    void everyGroupHasSomethingInIt() {
        YamlConfiguration file = Resources.read(FILE);
        for (String group : file.getKeys(false)) {
            assertTrue(!file.getStringList(group).isEmpty(), group + " is empty");
        }
    }

    /** Every node in the file, the families included. */
    private static Set<String> listed() {
        YamlConfiguration file = Resources.read(FILE);
        Set<String> nodes = new TreeSet<>();
        for (String group : file.getKeys(false)) {
            nodes.addAll(file.getStringList(group));
        }
        assertTrue(file.getKeys(false).contains(FAMILIES), FILE + " has no " + FAMILIES + " group");
        return nodes;
    }

    private static Set<String> inSources() throws IOException {
        Set<String> nodes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Resources.SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = NODE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    nodes.add(matcher.group(1));
                }
            }
        }
        nodes.remove("chorus.");
        assertTrue(nodes.size() > 100, "the scanner found almost nothing, so it has stopped working");
        return nodes;
    }
}
