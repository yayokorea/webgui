package land.webgui;

import land.webgui.common.WebGUILog;
import land.webgui.common.WebviewProtocol;
import land.webgui.server.WebGUIUpdateChecker;
import land.webgui.server.WebviewServerConfig;
//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;*/
//? }
import org.slf4j.Logger;

//? if !fabric {
/*@Mod(WebGUIMod.MOD_ID)*/
//? }
public final class WebGUIMod
        //? if fabric {
        implements ModInitializer
        //? }
{
    public static final String MOD_ID = WebviewProtocol.NAMESPACE;
    public static final Logger LOGGER = WebGUILog.LOGGER;

    // The shared config classes do not know where a mod loader keeps its files; hand them
    // the directory before the first load().
    private static void initServerFiles(java.nio.file.Path configDir) {
        WebviewServerConfig.init(configDir);
        EntityBindingStore.init(configDir);
    }

    //? if fabric {
    @Override
    public void onInitialize() {
        // First, before anything can ask for either: the client entrypoint reads the
        // version too, and Fabric runs this one before it.
        WebGUIVersion.init(() -> FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(""));
        initServerFiles(FabricLoader.getInstance().getConfigDir().resolve("webgui"));
        WebviewNetworking.registerPayloadTypes();
        WebviewNetworking.registerServerReceivers();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            WebviewServerConfig.load();
            EntityBindingStore.load();
            land.webgui.server.WebviewAssets.reload();
        });
        EntityInteractionListener.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WebGUIUpdateChecker.checkAsync();
        });
        WebviewCommands.register();
        WebviewJoinHud.register();
        land.webgui.server.WebviewDeathListener.register();
        LOGGER.info("WebGUI common init (S2C payloads, commands).");
    }
    //? } else {
    /*public WebGUIMod(IEventBus modBus) {
        // First, before anything can ask for either: the client init below reads the
        // version too.
        WebGUIVersion.init(() -> ModList.get()
                .getModContainerById(MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse(""));
        initServerFiles(FMLPaths.CONFIGDIR.get().resolve("webgui"));
        // Registers C2S + S2C types; S2C receive-handlers are wired client-side
        // only inside registerPayloadTypes (see the note there).
        WebviewNetworking.registerPayloadTypes(modBus);
        EntityInteractionListener.register();
        WebviewCommands.register();
        WebviewJoinHud.register();
        land.webgui.server.WebviewDeathListener.register();
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        //? if >=1.21.5 {
        Dist dist = FMLEnvironment.getDist();
        //? } else {
        Dist dist = FMLEnvironment.dist;
        //? }
        if (dist == Dist.CLIENT) {
            WebGUIClient.initClient(modBus);
        }
        LOGGER.info("WebGUI common init (S2C payloads, commands).");
    }

    private void onServerStarting(ServerStartingEvent event) {
        WebviewServerConfig.load();
        EntityBindingStore.load();
        land.webgui.server.WebviewAssets.reload();
    }

    private void onServerStarted(ServerStartedEvent event) {
        WebGUIUpdateChecker.checkAsync();
    }*/
    //? }
}
