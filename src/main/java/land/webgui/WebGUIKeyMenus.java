package land.webgui;

import com.mojang.blaze3d.platform.InputConstants;
import land.webgui.common.KeyMenu;
import land.webgui.compat.Mc;
import land.webgui.keys.KeyCombo;
import land.webgui.keys.KeyComboTracker;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The key combinations a server defined, and the handling of the keys that complete them.
 *
 * <p>Not {@code KeyMapping}s: a mapping holds one key, is registered once at start-up, and its
 * number cannot follow a server's list. The game's key events are watched instead (see the
 * {@code KeyboardMixin}), which also lets a combination be any keys held together.
 *
 * <p>The list arrives on join and lives until the player leaves; what a player rebinds is kept
 * in {@link WebGUIClientConfig}, per id, and beats the server's keys.
 */
public final class WebGUIKeyMenus {

    private record Bound(KeyMenu menu, KeyCombo combo) {}

    private static final KeyComboTracker TRACKER = new KeyComboTracker();

    private static List<KeyMenu> menus = List.of();
    private static List<Bound> bound = List.of();

    private WebGUIKeyMenus() {}

    /** The server's list, as the last join or reload sent it. */
    public static List<KeyMenu> menus() {
        return menus;
    }

    public static void set(List<KeyMenu> fromServer) {
        menus = List.copyOf(fromServer);
        rebuild();
    }

    public static void clear() {
        menus = List.of();
        bound = List.of();
        TRACKER.clear();
    }

    /** The keys that open this menu for this player: their own choice if they made one, else the server's. */
    public static List<String> keysFor(KeyMenu menu) {
        List<String> own = WebGUIClientConfig.keyOverride(menu.id());
        return own != null ? own : menu.keys();
    }

    /** Re-reads the player's overrides. Called after one changes. */
    public static void rebuild() {
        List<Bound> next = new ArrayList<>();
        for (KeyMenu menu : menus) {
            // An empty override unbinds the menu, and a key this version cannot press has no
            // combination: either way there is nothing to match.
            KeyCombo combo = KeyCombo.of(keysFor(menu));
            if (combo != null) {
                next.add(new Bound(menu, combo));
            }
        }
        bound = next;
    }

    /**
     * Sees one key event from the game's keyboard handler, before the game does.
     *
     * @return true if the event completed a combination and should not reach the game, so that
     *         the last key pressed does nothing of its own (an F would otherwise swap hands)
     */
    public static boolean onKey(Minecraft client, int key, int action) {
        if (action == InputConstants.RELEASE) {
            TRACKER.release(key);
            return false;
        }
        // Repeats are neither a new press nor a release.
        if (action != InputConstants.PRESS) {
            return false;
        }
        // Tracked even while a screen is open, so keys held when it closes are known.
        Bound hit = TRACKER.press(key, bound, Bound::combo);
        if (hit == null || !canOpen(client)) {
            return false;
        }
        // Not now: the character for this key is still on its way, and a page that opened
        // first would have it typed into it.
        client.execute(() -> WebGUIKeys.openPage(client, hit.menu().url()));
        return true;
    }

    private static boolean canOpen(Minecraft client) {
        return client.player != null && !Mc.hasScreen(client) && !WebHudOverlay.isHudInteractive();
    }
}
