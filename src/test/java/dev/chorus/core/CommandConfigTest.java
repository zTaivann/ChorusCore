package dev.chorus.core;

import dev.chorus.core.feedback.ParticleCue;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds plugin.yml, aliases.yml and the module files to the same command list, and guards the
 * two Bukkit traps that this plugin's design depends on avoiding.
 */
class CommandConfigTest {

    private static final Pattern SOUND_KEY = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)+");

    /** The two commands the core itself owns have no warmup, cooldown or price. */
    private static final Set<String> UNPRICED = Set.of("chorus", "commands");

    private static final Set<String> OPTIONS =
            Set.of("enabled", "warmup-seconds", "cooldown-seconds", "price", "sound", "particle");

    private static Set<String> declaredCommands() {
        return new TreeSet<>(Resources.section(Resources.read("plugin.yml"), "commands").getKeys(false));
    }

    @Test
    void pluginYmlDeclaresNoPermissionsOrAliases() {
        ConfigurationSection commands = Resources.section(Resources.read("plugin.yml"), "commands");
        for (String command : commands.getKeys(false)) {
            ConfigurationSection block = commands.getConfigurationSection(command);
            assertNotNull(block, "/" + command + " has no block in plugin.yml");
            // A permission here would make Bukkit refuse the command with its own message,
            // and aliases here would split them across two files.
            assertTrue(!block.contains("permission"),
                    "/" + command + " declares a permission in plugin.yml; check it in code instead");
            assertTrue(!block.contains("aliases"),
                    "/" + command + " declares aliases in plugin.yml; they belong in aliases.yml");
        }
    }

    @Test
    void aliasesCoverEveryCommandAndNothingElse() {
        YamlConfiguration aliases = Resources.read("aliases.yml");
        Set<String> declared = declaredCommands();

        assertEquals(List.of(), declared.stream().filter(name -> !aliases.contains(name)).toList(),
                "aliases.yml has no entry for these commands");
        assertEquals(List.of(), aliases.getKeys(false).stream()
                        .filter(name -> !declared.contains(name)).toList(),
                "aliases.yml names commands that do not exist");
    }

    @Test
    void noAliasClashes() {
        YamlConfiguration aliases = Resources.read("aliases.yml");
        Set<String> taken = new HashSet<>(declaredCommands());
        List<String> clashes = new ArrayList<>();

        for (String command : aliases.getKeys(false)) {
            for (String alias : aliases.getStringList(command)) {
                if (!taken.add(alias)) {
                    clashes.add(alias + " on /" + command);
                }
            }
        }
        assertEquals(List.of(), clashes, "these aliases collide with another command or alias");
    }

    @Test
    void everyModuleHasCompleteDefaults() {
        for (String path : Resources.MODULES) {
            YamlConfiguration module = Resources.read(path);
            assertTrue(module.isBoolean("enabled"), path + " has no module-wide 'enabled' switch");

            ConfigurationSection defaults =
                    Resources.section(module, "commands").getConfigurationSection("defaults");
            assertNotNull(defaults, path + " has no defaults block");
            for (String option : List.of("enabled", "warmup-seconds", "cooldown-seconds", "price",
                    "sound.key", "sound.volume", "sound.pitch", "particle.name", "particle.count",
                    "particle.spread", "particle.height", "particle.speed")) {
                assertTrue(defaults.contains(option), path + " defaults are missing " + option);
            }
        }
    }

    @Test
    void everyCommandHasRulesAndOnlyKnownOptions() {
        Set<String> declared = declaredCommands();
        Set<String> configured = new TreeSet<>();
        List<String> problems = new ArrayList<>();

        for (String path : Resources.MODULES) {
            ConfigurationSection blocks = Resources.section(Resources.read(path), "commands");
            for (String command : blocks.getKeys(false)) {
                if (command.equals("defaults")) {
                    continue;
                }
                if (!configured.add(command)) {
                    problems.add("/" + command + " has a rules block in two module files");
                }
                if (!declared.contains(command)) {
                    problems.add(path + " configures /" + command + ", which is not in plugin.yml");
                }

                ConfigurationSection block = blocks.getConfigurationSection(command);
                if (block == null) {
                    continue;
                }
                block.getKeys(false).stream()
                        .filter(option -> !OPTIONS.contains(option))
                        .forEach(option -> problems.add(
                                path + " has an unknown option '" + option + "' on /" + command));
            }
        }

        declared.stream()
                .filter(name -> !UNPRICED.contains(name) && !configured.contains(name))
                .forEach(name -> problems.add("/" + name + " has no rules block in any module file"));

        assertEquals(List.of(), problems);
    }

    /**
     * Runs against the 1.18.2 API, the oldest version this jar supports, so a default that
     * only exists on newer builds fails here rather than on somebody's server.
     */
    @Test
    void everyConfiguredEffectExistsOnTheOldestVersion() {
        List<String> problems = new ArrayList<>();

        for (String path : Resources.MODULES) {
            ConfigurationSection blocks = Resources.section(Resources.read(path), "commands");
            for (String command : blocks.getKeys(false)) {
                ConfigurationSection block = blocks.getConfigurationSection(command);
                if (block == null) {
                    continue;
                }

                String sound = block.getString("sound.key", "");
                if (!sound.isEmpty() && !SOUND_KEY.matcher(sound).matches()) {
                    problems.add("'" + sound + "' on /" + command
                            + " is not shaped like a Minecraft sound name");
                }

                String particle = block.getString("particle.name", "");
                if (!particle.isEmpty()
                        && ParticleCue.read(block, ParticleCue.NONE, name -> { }).particle() == null) {
                    problems.add("particle '" + particle + "' on /" + command
                            + " does not exist on the oldest supported version");
                }
            }
        }
        assertEquals(List.of(), problems);
    }
}
