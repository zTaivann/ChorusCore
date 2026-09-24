package dev.chorus.core.menu;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Asks a player for a value in chat, and takes the answer without letting it become a message. */
public final class ChatPrompts implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String CANCEL = "cancel";
    private static final long SWEEP_TICKS = 20L * 10;
    private static final long TIMEOUT_MILLIS = 60_000;

    private final Plugin plugin;
    private final Messages messages;
    private final Schedulers schedulers;
    private final Map<UUID, Prompt> waiting = new ConcurrentHashMap<>();

    private ChorusTask sweeper;

    public ChatPrompts(Plugin plugin, Messages messages, Schedulers schedulers) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedulers = schedulers;
    }

    public void start() {
        sweeper = schedulers.globalTimer(this::dropExpired,
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
     * @param answer  given the typed line, on the player's own thread
     * @param aborted run instead when they change their mind or run out of time
     */
    public void ask(Player player, Component ask, Consumer<String> answer, Runnable aborted) {
        player.closeInventory();
        waiting.put(player.getUniqueId(),
                new Prompt(answer, aborted, System.currentTimeMillis() + TIMEOUT_MILLIS));

        player.sendMessage(ask);
        messages.send(player, "core.prompt-cancel");
    }

    /** First of all listeners, so an answer never reaches a chat plugin or another player. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = waiting.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);

        String typed = PLAIN.serialize(event.message()).trim();
        schedulers.entity(player, () -> {
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

    public static String normalise(String typed) {
        return typed.toLowerCase(Locale.ROOT);
    }

    private record Prompt(Consumer<String> answer, Runnable aborted, long expiresAt) {
    }
}
