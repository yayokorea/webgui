package land.webgui.paper;

import land.webgui.common.DeathInfoJson;
import land.webgui.server.WebviewServerConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Reports a player's death to their custom death page, as {@code WebviewDeathListener} does in
 * the mod. Runs only when the server configured {@code deathScreenUrl}.
 *
 * <b>Order against the vanilla death packet.</b> The mod sends this from Fabric's AFTER_DEATH,
 * which is injected at the tail of {@code ServerPlayer.die}, after the combat-kill packet that
 * opens the vanilla death screen: there the info arrives just after the screen is opened.
 * {@code PlayerDeathEvent} fires at the other end, early in {@code die}, so here it arrives just
 * before. That is fine, and nothing is deferred: the client keeps the info until a death page
 * has loaded and replays it then (WebGUIDeathScreen.pendingInfo, read from the page-load
 * hook), so it does not matter whether the screen or the info comes first. Arriving first is
 * the safer of the two, as there is no window in which a quick page load could miss it.
 * What the client does need before the death, the page URL, was sent on join.
 */
public final class PaperDeathListener implements Listener {
    private static final String DEATH_KEY_PREFIX = "death.attack.";

    // MONITOR and ignoring cancelled: a plugin that cancels the death leaves the player alive,
    // and a living player must not be handed a death page. (Paper's PlayerDeathEvent can be cancelled.)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        String url = WebviewServerConfig.deathScreenUrl();
        if (url.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        PaperNetworking.sendDeathScreen(player, url, describe(event));
    }

    private static String describe(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        DamageSource source = event.getDamageSource();
        return DeathInfoJson.buildJson(
                causeOf(source),
                deathMessage(event, source),
                player.getWorld().isHardcore(),
                killer(source));
    }

    /**
     * What the mod calls the cause: the damage type's {@code msgId} (fall, mob, player,
     * genericKill), which is not the damage type's registry id ({@code mob_attack}).
     * Bukkit exposes the msgId only inside the translation key, "death.attack.&lt;msgId&gt;".
     */
    private static String causeOf(DamageSource source) {
        return causeFromTranslationKey(source.getDamageType().getTranslationKey());
    }

    static String causeFromTranslationKey(String key) {
        if (key == null) {
            return null;
        }
        return key.startsWith(DEATH_KEY_PREFIX) ? key.substring(DEATH_KEY_PREFIX.length()) : key;
    }

    /**
     * The message the server would broadcast, in English. Another plugin may have removed it
     * (a null message means "broadcast nothing"); the page still wants some text, so it is
     * rebuilt from the damage type the way vanilla words a plain death: the type's message with
     * the victim and, when there is one, the killer.
     */
    private static String deathMessage(PlayerDeathEvent event, DamageSource source) {
        Component message = event.deathMessage();
        if (message == null) {
            Entity attacker = source.getCausingEntity();
            Component victim = event.getPlayer().name();
            message = attacker == null
                    ? Component.translatable(source.getDamageType().getTranslationKey(), victim)
                    : Component.translatable(source.getDamageType().getTranslationKey(), victim, attacker.name());
        }
        return PaperText.plain(message);
    }

    private static DeathInfoJson.Killer killer(DamageSource source) {
        // The causing entity, like the mod's getAttacker(): the skeleton, not its arrow.
        Entity attacker = source.getCausingEntity();
        if (attacker == null) {
            return DeathInfoJson.Killer.environment();
        }
        String name = PaperText.displayName(attacker);
        if (attacker instanceof Player) {
            return DeathInfoJson.Killer.player(name, attacker.getUniqueId());
        }
        return DeathInfoJson.Killer.mob(name, attacker.getUniqueId(), attacker.getType().getKey().toString());
    }
}
