package land.webgui.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.entity.Entity;

import java.util.Locale;

/**
 * Turns Adventure components into the plain English strings a Fabric server produces with
 * {@code Text.getString()}.
 *
 * A Fabric server renders translatable text from its built-in en_us language, whatever the
 * player's own language is, and so must this: a page keys on "Pig", not on "entity.minecraft.pig".
 */
final class PaperText {
    private PaperText() {}

    /** Null stays null, so callers can tell "no text" from empty text. */
    static String plain(Component component) {
        if (component == null) {
            return null;
        }
        // Translations other plugins registered come first. The vanilla keys that remain
        // ("entity.minecraft.pig") are rendered by Paper's plain serializer from the server's own
        // en_us language, which is what makes the result match a Fabric server's getString().
        return PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(component, Locale.US));
    }

    /**
     * The entity's custom name if it has one, else its type's name ("Pig") — the rule the mod
     * uses for the entity context. {@code Entity#name()} would give a player's profile name
     * where the mod says "Player", so the choice is made here.
     */
    static String entityName(Entity entity) {
        Component custom = entity.customName();
        return plain(custom != null ? custom : Component.translatable(entity.getType().translationKey()));
    }

    /** What the mod calls {@code attacker.getName()}: the profile name of a player, else {@code Entity#name()}. */
    static String displayName(Entity entity) {
        return plain(entity.name());
    }
}
