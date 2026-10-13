package land.webgui.paper;

import land.webgui.EntityBindingStore;
import land.webgui.WebGUIVersion;
import land.webgui.common.WebGUILog;
import land.webgui.server.WebviewAssets;
import land.webgui.server.WebviewServerConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

/**
 * WebGUI for Paper: lets a Paper server drive clients that run the WebGUI Fabric mod.
 *
 * The server needs nothing installed on the client's side beyond the mod itself, and the
 * client needs nothing it does not already have: the pages travel over plugin channels
 * the mod listens on. NeoForge clients are not supported here.
 */
public final class WebGUIPaperPlugin extends JavaPlugin {
    private PaperJoinFlow joinFlow;

    @Override
    public void onEnable() {
        // First, before anything can ask for it: the handshake reads the version on every join.
        WebGUIVersion.init(() -> getPluginMeta().getVersion());

        Path dataDir = getDataFolder().toPath();
        WebviewServerConfig.init(dataDir);
        EntityBindingStore.init(dataDir);
        // The order the mod uses when its server starts.
        WebviewServerConfig.load();
        EntityBindingStore.load();
        WebviewAssets.reload();

        PaperNetworking.registerChannels(this);

        joinFlow = new PaperJoinFlow(this);
        getServer().getPluginManager().registerEvents(joinFlow, this);

        getServer().getPluginManager().registerEvents(new PaperEntityBindings(), this);

        PluginCommand command = getCommand("webgui");
        if (command != null) {
            PaperCommands commands = new PaperCommands();
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }

        // Enabled with players already connected (a plugin manager, or /reload): their
        // clients registered long ago and will not do it again, so nothing would ever greet them.
        joinFlow.adoptOnlinePlayers();

        WebGUILog.LOGGER.info("WebGUI {} enabled; settings in {}/server.json",
                WebGUIVersion.current(), WebviewServerConfig.displayDir());
    }

    @Override
    public void onDisable() {
        if (joinFlow != null) {
            joinFlow.shutdown();
            joinFlow = null;
        }
        PaperNetworking.unregisterChannels(this);
    }
}
