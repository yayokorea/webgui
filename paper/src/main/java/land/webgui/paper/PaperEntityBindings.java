package land.webgui.paper;

import land.webgui.EntityBindingStore;
import land.webgui.common.EntityContextJson;
import land.webgui.common.Placeholders;
import land.webgui.server.EntityBinding;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Right-clicking a bound entity opens its page, as {@code EntityInteractionListener} does in
 * the mod.
 *
 * One click reaches the server as up to two packets, and Bukkit turns each into its own event:
 * <ul>
 *   <li>{@link PlayerInteractAtEntityEvent} for INTERACT_AT, the click with its hit position.
 *       A vanilla client sends it for every click on an entity.</li>
 *   <li>{@link PlayerInteractEntityEvent} for INTERACT, which the client sends after it unless
 *       its own prediction already consumed the click (an armor stand does; a villager, a pig
 *       or a zombie do not). So most entities get both events for one click, an armor stand
 *       only the first.</li>
 * </ul>
 * Cancelling one packet's event stops only what that packet would have done: the armor stand's
 * equipment swap runs from INTERACT_AT, a villager's trade from INTERACT. Hence the two roles
 * here: INTERACT_AT opens the page, so it opens once and also for the armor stand, and both
 * events honour {@code cancelInteraction}.
 *
 * Whether the two classes share a handler list differs by version. Up to Paper 1.21.x
 * {@code PlayerInteractAtEntityEvent} has its own list, so a listener for the parent class never
 * sees it. From 26.x it has none of its own and the parent's handlers receive both. The parent
 * handler therefore skips the subclass explicitly, which is right in both cases.
 */
public final class PaperEntityBindings implements Listener {

    // HIGH, not MONITOR: this cancels. After the plugins that protect an entity (they run at
    // NORMAL and below), and ignoring a click they already cancelled — a claim that denies the
    // click should not pop up a page either.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        Optional<EntityBinding> binding = bindingFor(event);
        if (binding.isEmpty()) {
            return;
        }
        open(event.getPlayer(), event.getRightClicked(), binding.get());
        if (binding.get().cancelInteraction()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return; // handled by onInteractAt; reaches here only where the handler lists are shared
        }
        Optional<EntityBinding> binding = bindingFor(event);
        if (binding.isPresent() && binding.get().cancelInteraction()) {
            event.setCancelled(true);
        }
    }

    private static Optional<EntityBinding> bindingFor(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return Optional.empty();
        }
        return EntityBindingStore.get(event.getRightClicked().getUniqueId());
    }

    private static void open(Player player, Entity entity, EntityBinding binding) {
        String typeId = entity.getType().getKey().toString();
        String url = Placeholders.resolve(binding.urlTemplate(),
                player.getName(), player.getUniqueId(), entity.getUniqueId(), typeId);
        Location at = entity.getLocation();
        String entityJson = EntityContextJson.buildJson(entity.getUniqueId(), typeId,
                PaperText.entityName(entity), at.getX(), at.getY(), at.getZ());
        PaperNetworking.openGuiForEntity(player, url, entityJson);
    }
}
