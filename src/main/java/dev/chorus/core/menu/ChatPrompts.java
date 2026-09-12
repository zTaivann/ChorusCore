package dev.chorus.core.menu;

import dev.chorus.core.locale.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Asks a player for a value in chat, and takes the answer without letting it become a message.
 *
 * <p>This is what makes a screen a screen. A menu that sends you away to type a command has
 * only moved the typing somewhere else; clicking a button, being asked for a number and
 * typing the number is the whole point.
 *
 * <p>The answer arrives on the chat thread and is handed back on the server thread, because
 * everything it goes on to do — writing a file, reopening an inventory — belongs there.
 */
public final class ChatPrompts implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String CANCEL = "cancel";
    private static final long SWEEP_TICKS = 20L * 10;
    private static final long TIMEOUT_MILLIS = 60_000;

    private final Plugin plugin;
    private final Messages messages;
    private final Executor mainThread;
    private final Map<UUID, Prompt> waiting = new ConcurrentHashMap<>();

    private BukkitTask sweeper;

    public ChatPrompts(Plugin plugin, Messages messages, Executor mainThread) {
        this.plugin = plugin;
        this.messages = messages;
        this.mainThread = mainThread;
    }

    public void start() {
        sweeper = plugin.getServer().getScheduler().runTaskTimer(plugin, this::dropExpired,
                SWEEP_TICKS, SWEEP_TICKS);
    }

    public void shutdown() {
        if (sweeper != null) {
            sweeper.cancel();
        }
        waiting.clear();
    }

    /**
     * Closes whatever the player is looking at and waits for them to type.
     *
     * @param ask     the line telling them what to write, ready to send
     * @param answer  given the typed line, on the server thread
     * @param aborted run instead when they change their mind or run out of time
     */
    public void ask(Player player, Component ask, Consumer<String> answer, Runnable aborted) {
        player.closeInventory();
        waiting.put(player.getUniqueId(),
                new Prompt(answer, aborted, System.currentTimeMillis() + TIMEOUT_MILLIS));

        player.sendMessage(ask);
        messages.send(player, "core.prompt-cancel");
    }

    /**
     * Runs before anything else so the line never reaches a chat plugin, and never reaches
     * the other players either. Somebody typing a permission node into a prompt has not said
     * anything to anyone.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        // Concurrent because the answer arrives on the chat thread while everything that
        // sets a prompt up runs on the server one.
        Prompt prompt = waiting.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);

        String typed = PLAIN.serialize(event.message()).trim();
        mainThread.execute(() -> {
            if (typed.equalsIgnoreCase(CANCEL)) {
                prompt.aborted().run();
                return;
            }
            prompt.answer().accept(typed);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }

    /** Nobody should be silently swallowing a player's chat an hour after they forgot. */
    private void dropExpired() {
        long now = System.currentTimeMillis();
        waiting.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt() > now) {
                return false;
            }
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                messages.send(player, "core.prompt-expired");
            }
            return true;
        });
    }

    /** Lower-cased once here rather than at every call site. */
    public static String normalise(String typed) {
        return typed.toLowerCase(Locale.ROOT);
    }

    private record Prompt(Consumer<String> answer, Runnable aborted, long expiresAt) {
    }
}
