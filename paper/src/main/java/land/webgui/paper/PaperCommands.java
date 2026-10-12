package land.webgui.paper;

import land.webgui.EntityBindingStore;
import land.webgui.paper.CommandInput.ParseException;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code /webgui}, with the same tree and the same feedback as the mod's command:
 * <pre>
 * /webgui gui &lt;targets&gt; &lt;url...&gt;
 * /webgui hud &lt;targets&gt; &lt;url...&gt;
 * /webgui bind entity &lt;selector&gt; &lt;url&gt; [cancel_interaction]
 * /webgui unbind entity &lt;selector&gt;
 * /webgui reload
 * </pre>
 * Each subcommand has its own permission ({@code webgui.command.<name>}); entity selectors
 * additionally need whatever the server demands for selectors (vanilla's
 * {@code minecraft.command.selector}).
 */
final class PaperCommands implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("gui", "hud", "bind", "unbind", "reload");
    private static final List<String> SELECTORS = List.of("@a", "@p", "@r", "@s", "@e");

    private static String permission(String subcommand) {
        return "webgui.command." + subcommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            return false; // plugin.yml's usage line
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            return false;
        }
        if (!sender.hasPermission(permission(sub))) {
            fail(sender, "You do not have permission to use /" + label + " " + sub);
            return true;
        }
        try {
            CommandInput in = new CommandInput(args);
            in.readWord(); // the subcommand itself
            switch (sub) {
                case "gui", "hud" -> open(sender, sub.equals("hud"), in);
                case "bind" -> bind(sender, in);
                case "unbind" -> unbind(sender, in);
                case "reload" -> reload(sender);
                default -> { }
            }
        } catch (ParseException e) {
            fail(sender, e.getMessage());
        } catch (IllegalArgumentException e) {
            // A selector Bukkit could not parse, or one this sender may not use.
            fail(sender, e.getMessage());
        }
        return true;
    }

    private static void open(CommandSender sender, boolean hud, CommandInput in) throws ParseException {
        String selector = in.readSelector();
        String url = requireUrl(in);
        List<Player> players = selectPlayers(sender, selector);
        for (Player p : players) {
            if (hud) {
                PaperNetworking.openHud(p, url);
            } else {
                PaperNetworking.openGui(p, url);
            }
        }
        Command.broadcastCommandMessage(sender, (hud ? "Web HUD → " : "Web GUI → ") + players.size() + " player(s)");
    }

    private static void bind(CommandSender sender, CommandInput in) throws ParseException {
        expectEntity(in);
        String selector = in.readSelector();
        String url = in.readString();
        boolean cancel = in.hasMore() && in.readBool();
        if (in.hasMore()) {
            throw new ParseException("Unexpected argument '" + in.readWord() + "'");
        }
        List<Entity> entities = selectEntities(sender, selector);
        EntityBinding binding = new EntityBinding(url, cancel);
        for (Entity e : entities) {
            EntityBindingStore.bind(e.getUniqueId(), binding);
        }
        Command.broadcastCommandMessage(sender, "WebGUI: bound " + entities.size() + " entity/entities → " + url);
    }

    private static void unbind(CommandSender sender, CommandInput in) throws ParseException {
        expectEntity(in);
        String selector = in.readSelector();
        if (in.hasMore()) {
            throw new ParseException("Unexpected argument '" + in.readWord() + "'");
        }
        int removed = 0;
        for (Entity e : selectEntities(sender, selector)) {
            if (EntityBindingStore.unbind(e.getUniqueId())) removed++;
        }
        Command.broadcastCommandMessage(sender, "WebGUI: unbound " + removed + " entity/entities");
    }

    private static void reload(CommandSender sender) {
        String msg = WebviewServerConfig.reload();
        EntityBindingStore.load();
        // Every setting the client holds a copy of has to be pushed again, or online
        // players keep the old one. (The reload above also rescans the bundled pages.)
        for (Player p : Bukkit.getOnlinePlayers()) {
            PaperNetworking.sendConfigSnapshot(p);
        }
        Command.broadcastCommandMessage(sender, msg);
    }

    private static void expectEntity(CommandInput in) throws ParseException {
        String word = in.readWord();
        if (!word.equals("entity")) {
            throw new ParseException(word.isEmpty() ? "Expected 'entity'" : "Unknown argument '" + word + "', expected 'entity'");
        }
    }

    private static String requireUrl(CommandInput in) throws ParseException {
        String url = in.readRest();
        if (url.isEmpty()) {
            throw new ParseException("Expected a URL");
        }
        return url;
    }

    // --- Selectors ---

    private static List<Player> selectPlayers(CommandSender sender, String selector) {
        List<Player> players = new ArrayList<>();
        for (Entity e : resolve(sender, selector)) {
            if (!(e instanceof Player p)) {
                throw new IllegalArgumentException(
                        "Only players may be affected by this command, but the provided selector includes entities");
            }
            players.add(p);
        }
        if (players.isEmpty()) {
            throw new IllegalArgumentException("No player was found");
        }
        return players;
    }

    private static List<Entity> selectEntities(CommandSender sender, String selector) {
        List<Entity> entities = resolve(sender, selector);
        if (entities.isEmpty()) {
            throw new IllegalArgumentException("No entity was found");
        }
        return entities;
    }

    /** Vanilla selector syntax, plus what vanilla also accepts bare: a player's name or an entity's UUID. */
    private static List<Entity> resolve(CommandSender sender, String selector) {
        if (selector.startsWith("@")) {
            return Bukkit.selectEntities(sender, selector);
        }
        Entity found = null;
        try {
            found = Bukkit.getEntity(UUID.fromString(selector));
        } catch (IllegalArgumentException notAUuid) {
            found = Bukkit.getPlayerExact(selector);
        }
        return found == null ? List.of() : List.of(found);
    }

    private static void fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    // --- Tab completion ---

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return complete(String.join(" ", args), sender::hasPermission,
                () -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    /**
     * What to offer for the line typed so far (everything after {@code /webgui }).
     * Takes its dependencies as arguments so it can be tested without a server.
     */
    static List<String> complete(String line, Predicate<String> hasPermission, Supplier<List<String>> playerNames) {
        CommandInput.Split split = CommandInput.split(line);
        List<String> done = split.completed();
        String prefix = split.partial().toLowerCase(Locale.ROOT);

        List<String> options = new ArrayList<>();
        if (done.isEmpty()) {
            for (String sub : SUBCOMMANDS) {
                if (hasPermission.test(permission(sub))) options.add(sub);
            }
        } else {
            String sub = done.get(0).toLowerCase(Locale.ROOT);
            if (SUBCOMMANDS.contains(sub) && hasPermission.test(permission(sub))) {
                int index = done.size(); // index of the word being typed
                switch (sub) {
                    case "gui", "hud" -> {
                        if (index == 1) addTargets(options, playerNames);
                    }
                    case "bind", "unbind" -> {
                        if (index == 1) options.add("entity");
                        else if (index == 2) addTargets(options, playerNames);
                        else if (index == 4 && sub.equals("bind")) options.addAll(List.of("true", "false"));
                    }
                    default -> { }
                }
            }
        }
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static void addTargets(List<String> options, Supplier<List<String>> playerNames) {
        options.addAll(playerNames.get());
        options.addAll(SELECTORS);
    }
}
