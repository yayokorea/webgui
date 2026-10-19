package land.webgui.common;

/**
 * Everything both ends of the wire have to agree on: the version, the limits and the
 * channel names.
 *
 * One place on purpose. The Fabric and NeoForge codecs and the hand-written
 * {@link WebviewWire} used by servers without the mod all read their limits from here,
 * so a limit changed in one cannot quietly stay old in another.
 */
public final class WebviewProtocol {
    private WebviewProtocol() {}

    /**
     * 2 added {@code key_menus}. Clients and servers on different versions drop each
     * other's pages and say so on join (see the loader-side {@code WebGUIHandshake}).
     */
    public static final int PROTOCOL_VERSION = 2;

    /** displayMode: open as a screen. */
    public static final int MODE_GUI = 0;
    /** displayMode: open as an overlay. */
    public static final int MODE_HUD = 1;

    public static final int MAX_URL_LENGTH = 16384;
    public static final int MAX_EVENT_NAME_LENGTH = 256;
    public static final int MAX_EVENT_DATA_LENGTH = 32_768;
    public static final int MAX_VERSION_LENGTH = 64;
    /**
     * The whole {@code keyMenus} list as JSON, sent once per join. Large enough for hundreds of
     * entries; the sender drops what does not fit rather than failing.
     */
    public static final int MAX_KEY_MENUS_LENGTH = 128 * 1024;
    /** One asset path. Longer than any sane bundle nests, short enough to bound the manifest. */
    public static final int MAX_ASSET_PATH_LENGTH = 256;
    /** The whole file list, sent once per join. Half of a custom payload's 1 MiB ceiling. */
    public static final int MAX_MANIFEST_LENGTH = 512 * 1024;
    /**
     * Bytes per asset chunk.
     *
     * Well under the ceiling on either direction, and small enough that one player's
     * download does not sit in front of everyone else's gameplay packets.
     */
    public static final int ASSET_CHUNK_BYTES = 32 * 1024;

    public static final String NAMESPACE = "webgui";

    // The path half, for loaders that build their own identifier type from a namespace
    // and a path...
    public static final String PATH_OPEN_WEB        = "open_web";
    public static final String PATH_SET_MAIN_MENU   = "set_main_menu";
    public static final String PATH_EMIT_TO_PAGE    = "emit_to_page";
    public static final String PATH_PAGE_EVENT      = "page_event";
    public static final String PATH_ENTITY_CONTEXT  = "entity_context";
    public static final String PATH_TRUSTED_ORIGINS = "trusted_origins";
    public static final String PATH_DEATH_SCREEN    = "death_screen";
    public static final String PATH_KEY_MENUS       = "key_menus";
    public static final String PATH_HELLO           = "hello";
    public static final String PATH_ASSET_MANIFEST  = "asset_manifest";
    public static final String PATH_ASSET_REQUEST   = "asset_request";
    public static final String PATH_ASSET_CHUNK     = "asset_chunk";

    // ...and the whole name, for the ones that register a channel by string (Bukkit).
    public static final String CHANNEL_OPEN_WEB        = NAMESPACE + ":" + PATH_OPEN_WEB;
    public static final String CHANNEL_SET_MAIN_MENU   = NAMESPACE + ":" + PATH_SET_MAIN_MENU;
    public static final String CHANNEL_EMIT_TO_PAGE    = NAMESPACE + ":" + PATH_EMIT_TO_PAGE;
    public static final String CHANNEL_PAGE_EVENT      = NAMESPACE + ":" + PATH_PAGE_EVENT;
    public static final String CHANNEL_ENTITY_CONTEXT  = NAMESPACE + ":" + PATH_ENTITY_CONTEXT;
    public static final String CHANNEL_TRUSTED_ORIGINS = NAMESPACE + ":" + PATH_TRUSTED_ORIGINS;
    public static final String CHANNEL_DEATH_SCREEN    = NAMESPACE + ":" + PATH_DEATH_SCREEN;
    public static final String CHANNEL_KEY_MENUS       = NAMESPACE + ":" + PATH_KEY_MENUS;
    public static final String CHANNEL_HELLO           = NAMESPACE + ":" + PATH_HELLO;
    public static final String CHANNEL_ASSET_MANIFEST  = NAMESPACE + ":" + PATH_ASSET_MANIFEST;
    public static final String CHANNEL_ASSET_REQUEST   = NAMESPACE + ":" + PATH_ASSET_REQUEST;
    public static final String CHANNEL_ASSET_CHUNK     = NAMESPACE + ":" + PATH_ASSET_CHUNK;
}
