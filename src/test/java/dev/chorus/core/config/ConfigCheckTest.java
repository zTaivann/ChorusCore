package dev.chorus.core.config;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the startup check reports, and what it leaves alone. */
class ConfigCheckTest {

    private static final String FILE = "modules/test.yml";

    private static final String SHIPPED = """
            enabled: true
            homes:
              default-limit: 3
              default-name: home
              menu:
                icon: LIME_BED
            commands:
              defaults:
                cooldown-seconds: 0
                sound:
                  key: ui.button.click
              home:
                enabled: true
            """;

    /** Where the files that would be inside the jar are read from. */
    private static final java.nio.file.Path RESOURCES =
            java.nio.file.Path.of("src", "main", "resources");

    @TempDir
    File dataFolder;

    @Test
    void aKeyTheJarHasNeverHeardOfIsReported() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                homes:
                  default-limit: 5
                  defualt-name: home
                """);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("'defualt-name' is not an option"));
    }

    /** A name one slip away from a real option is worth saying out loud. */
    @Test
    void aTypoIsToldWhatItMeant() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                commands:
                  defaults:
                    cooldown-second: 5
                """);

        assertEquals("cooldown-seconds", problems.get(0).hint());
        assertTrue(problems.get(0).describe().contains("Did you mean 'cooldown-seconds'?"));
    }

    @Test
    void theLineItIsOnIsReported() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                homes:
                  default-limit: 3
                  nonsense: 1
                """);

        assertEquals(4, problems.get(0).line());
        assertTrue(problems.get(0).describe().startsWith(FILE + ":4"));
    }

    @Test
    void aValueOfTheWrongKindIsReported() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                homes:
                  default-limit: plenty
                """);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("takes a number, not text"));
    }

    /** Bukkit reads a scalar written where text is expected as text, so it is not a mistake. */
    @Test
    void aNumberWrittenWhereTextIsExpectedIsLeftAlone() throws IOException {
        assertEquals(List.of(), check("""
                enabled: true
                homes:
                  default-name: 5
                """));
    }

    @Test
    void aMaterialThatDoesNotExistIsReported() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                homes:
                  menu:
                    icon: LIME_BEDD
                """);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("is not a material"));
    }

    @Test
    void aSoundThatIsNotShapedLikeOneIsReported() throws IOException {
        List<ConfigProblem> problems = check("""
                enabled: true
                commands:
                  defaults:
                    sound:
                      key: 'Entity Enderman Teleport'
                """);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("sound name"));
    }

    /** The lines a command says are message keys, and the jar has no list of those. */
    @Test
    void aMessagesBlockInsideACommandIsLeftAlone() throws IOException {
        assertEquals(List.of(), check("""
                enabled: true
                commands:
                  defaults:
                    cooldown-seconds: 0
                  home:
                    messages:
                      error.no-permission: '<red>No.'
                """));
    }

    @Test
    void aFileWithNothingWrongReportsNothing() throws IOException {
        assertEquals(List.of(), check(SHIPPED));
    }

    /** An entry in the worth list is a material, and a typo there is a price that never applies. */
    @Test
    void aMaterialNamedByAKeyIsChecked() throws IOException {
        write("modules/shops.yml", """
                shops:
                  worth:
                    diamond: 60.0
                    diamondd: 60.0
                """);
        List<ConfigProblem> problems = ConfigCheck.run(plugin(Map.of()), server(),
                List.of("modules/shops.yml"), false);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("'diamondd' is not a material"));
    }

    /** A file the jar does not carry is a server's own, and has no list of options to fail. */
    @Test
    void aFileThatIsNotInTheJarIsLeftAlone() throws IOException {
        write("modules/mine.yml", "whatever: true");
        assertEquals(List.of(), ConfigCheck.run(plugin(Map.of()), server(),
                List.of("modules/mine.yml"), false));
    }

    /** The files as they ship, which is what a fresh install has on disk. */
    @Test
    void theFilesTheJarShipsHaveNothingWrong() throws IOException {
        List<String> paths = new java.util.ArrayList<>(List.of("config.yml", "aliases.yml"));
        try (var listing = Files.list(RESOURCES.resolve("modules"))) {
            listing.map(file -> "modules/" + file.getFileName()).sorted().forEach(paths::add);
        }
        for (String path : paths) {
            write(path, Files.readString(RESOURCES.resolve(path), StandardCharsets.UTF_8));
        }

        assertEquals(List.of(), ConfigCheck.run(jarPlugin(), server(), paths, false));
    }

    /** A plugin whose jar is the resources folder, so the shipped files back themselves. */
    private Plugin jarPlugin() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder;
            case "getResource" -> {
                java.nio.file.Path file = RESOURCES.resolve((String) args[0]);
                yield Files.isRegularFile(file) ? Files.newInputStream(file) : null;
            }
            case "toString" -> "jar stub";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, handler);
    }

    private List<ConfigProblem> check(String onDisk) throws IOException {
        write(FILE, onDisk);
        return ConfigCheck.run(plugin(Map.of(FILE, SHIPPED)), server(), List.of(FILE), false);
    }

    private void write(String path, String text) throws IOException {
        File file = new File(dataFolder, path);
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private Plugin plugin(Map<String, String> inside) {
        Map<String, String> jar = new HashMap<>(inside);
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder;
            case "getResource" -> {
                String text = jar.get((String) args[0]);
                yield text == null ? null
                        : new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
            }
            case "toString" -> "plugin stub";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, handler);
    }

    /** Nothing here asks about worlds, so a server that answers anything is a mistake. */
    private static Server server() {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                new Class<?>[]{Server.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
