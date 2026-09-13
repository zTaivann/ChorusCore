package dev.chorus.core.text.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.text.ServerText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /motd} and {@code /info}: a text file from the plugin folder, a page at a time.
 *
 * <p>One class for both. They differ in nothing but which file they read and what they are
 * called, and writing the second one out again would only give the two of them room to drift
 * apart.
 */
public final class TextCommand extends ChorusCommand {

    private final ServerText text;

    public TextCommand(CommandSupport support, String name, ServerText text) {
        super(support, name, "chorus.utility." + name);
        this.text = text;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (text.isEmpty()) {
            messages.send(sender, "utility.text-empty");
            return;
        }

        ServerText.Chapter chapter;
        int page;
        if (args.length == 0 || number(args[0]) > 0) {
            chapter = text.opening();
            page = args.length == 0 ? 1 : number(args[0]);
            if (chapter == null) {
                listChapters(sender);
                return;
            }
        } else {
            chapter = text.chapter(args[0]);
            if (chapter == null) {
                messages.send(sender, "utility.text-unknown", "chapter", args[0]);
                return;
            }
            page = args.length > 1 ? Math.max(1, number(args[1])) : 1;
        }

        if (!chapter.readableBy(sender)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        show(sender, chapter, page);
        settle(sender);
    }

    /** Sent on join as well as on the command, so it takes the reader rather than assuming. */
    public void show(CommandSender reader, @Nullable ServerText.Chapter chapter, int page) {
        if (chapter == null) {
            return;
        }

        int pages = ServerText.pages(chapter);
        int wanted = Math.min(Math.max(1, page), pages);
        for (String line : ServerText.page(chapter, wanted, reader)) {
            reader.sendMessage(messages.parse(line));
        }

        if (pages > 1) {
            messages.send(reader, "utility.text-page",
                    "page", String.valueOf(wanted),
                    "pages", String.valueOf(pages),
                    "command", name());
        }
        if (chapter.name().isEmpty()) {
            listChapters(reader);
        }
    }

    public ServerText text() {
        return text;
    }

    private void listChapters(CommandSender sender) {
        List<String> chapters = text.readableNames(sender);
        if (chapters.isEmpty()) {
            return;
        }
        messages.send(sender, "utility.text-chapters",
                "chapters", String.join(", ", chapters), "command", name());
    }

    /** Zero when the word is not a page number, which is how a chapter name is told apart. */
    private static int number(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !allowed(sender)) {
            return List.of();
        }
        return startingWith(args[0], text.readableNames(sender));
    }
}
