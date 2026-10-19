package land.webgui;

import land.webgui.common.WebviewProtocol;

//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? }
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }

public final class WebviewPayloads {
    private WebviewPayloads() {}

    // Aliases: the values live in WebviewProtocol, which the Paper plugin shares, so that a
    // limit cannot differ between the codecs here and the hand-written ones there.
    public static final int MAX_EVENT_NAME_LENGTH = WebviewProtocol.MAX_EVENT_NAME_LENGTH;
    public static final int MAX_EVENT_DATA_LENGTH = WebviewProtocol.MAX_EVENT_DATA_LENGTH;
    public static final int MAX_VERSION_LENGTH = WebviewProtocol.MAX_VERSION_LENGTH;
    public static final int MAX_ASSET_PATH_LENGTH = WebviewProtocol.MAX_ASSET_PATH_LENGTH;
    public static final int MAX_MANIFEST_LENGTH = WebviewProtocol.MAX_MANIFEST_LENGTH;
    public static final int MAX_KEY_MENUS_LENGTH = WebviewProtocol.MAX_KEY_MENUS_LENGTH;
    public static final int ASSET_CHUNK_BYTES = WebviewProtocol.ASSET_CHUNK_BYTES;

    // Channel identifiers — used in both legacy (1.20.1) and modern networking
    //? if >=1.21.5 {
    public static final Identifier OPEN_WEB_CHANNEL       = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_OPEN_WEB);
    public static final Identifier MAIN_MENU_CHANNEL      = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_SET_MAIN_MENU);
    public static final Identifier EMIT_TO_PAGE_CHANNEL   = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_EMIT_TO_PAGE);
    public static final Identifier PAGE_EVENT_CHANNEL     = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_PAGE_EVENT);
    public static final Identifier ENTITY_CONTEXT_CHANNEL = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ENTITY_CONTEXT);
    public static final Identifier TRUSTED_ORIGINS_CHANNEL = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_TRUSTED_ORIGINS);
    public static final Identifier DEATH_SCREEN_CHANNEL   = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_DEATH_SCREEN);
    public static final Identifier KEY_MENUS_CHANNEL       = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_KEY_MENUS);
    public static final Identifier HELLO_CHANNEL          = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_HELLO);
    public static final Identifier ASSET_MANIFEST_CHANNEL = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_MANIFEST);
    public static final Identifier ASSET_REQUEST_CHANNEL  = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_REQUEST);
    public static final Identifier ASSET_CHUNK_CHANNEL    = Identifier.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_CHUNK);
    //? } else {
    /*//? if >=1.21 {
    public static final ResourceLocation OPEN_WEB_CHANNEL       = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_OPEN_WEB);
    public static final ResourceLocation MAIN_MENU_CHANNEL      = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_SET_MAIN_MENU);
    public static final ResourceLocation EMIT_TO_PAGE_CHANNEL   = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_EMIT_TO_PAGE);
    public static final ResourceLocation PAGE_EVENT_CHANNEL     = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_PAGE_EVENT);
    public static final ResourceLocation ENTITY_CONTEXT_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ENTITY_CONTEXT);
    public static final ResourceLocation TRUSTED_ORIGINS_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_TRUSTED_ORIGINS);
    public static final ResourceLocation DEATH_SCREEN_CHANNEL   = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_DEATH_SCREEN);
    public static final ResourceLocation KEY_MENUS_CHANNEL       = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_KEY_MENUS);
    public static final ResourceLocation HELLO_CHANNEL          = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_HELLO);
    public static final ResourceLocation ASSET_MANIFEST_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_MANIFEST);
    public static final ResourceLocation ASSET_REQUEST_CHANNEL  = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_REQUEST);
    public static final ResourceLocation ASSET_CHUNK_CHANNEL    = ResourceLocation.fromNamespaceAndPath(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_CHUNK);
    //? } else {
    public static final ResourceLocation OPEN_WEB_CHANNEL       = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_OPEN_WEB);
    public static final ResourceLocation MAIN_MENU_CHANNEL      = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_SET_MAIN_MENU);
    public static final ResourceLocation EMIT_TO_PAGE_CHANNEL   = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_EMIT_TO_PAGE);
    public static final ResourceLocation PAGE_EVENT_CHANNEL     = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_PAGE_EVENT);
    public static final ResourceLocation ENTITY_CONTEXT_CHANNEL = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ENTITY_CONTEXT);
    public static final ResourceLocation TRUSTED_ORIGINS_CHANNEL = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_TRUSTED_ORIGINS);
    public static final ResourceLocation DEATH_SCREEN_CHANNEL   = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_DEATH_SCREEN);
    public static final ResourceLocation KEY_MENUS_CHANNEL       = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_KEY_MENUS);
    public static final ResourceLocation HELLO_CHANNEL          = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_HELLO);
    public static final ResourceLocation ASSET_MANIFEST_CHANNEL = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_MANIFEST);
    public static final ResourceLocation ASSET_REQUEST_CHANNEL  = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_REQUEST);
    public static final ResourceLocation ASSET_CHUNK_CHANNEL    = new ResourceLocation(WebviewProtocol.NAMESPACE, WebviewProtocol.PATH_ASSET_CHUNK);
    //? }
    *///? }

    //? if >=1.20.5 {
    // S2C: server emits a named event to the page.
    public record WebviewEmitS2CPayload(String eventName, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEmitS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(EMIT_TO_PAGE_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEmitS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewEmitS2CPayload::eventName,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewEmitS2CPayload::jsonPayload,
                        WebviewEmitS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // C2S: page sends a named event to the server.
    public record WebviewPageEventC2SPayload(String channel, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewPageEventC2SPayload> TYPE =
                new CustomPacketPayload.Type<>(PAGE_EVENT_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewPageEventC2SPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewPageEventC2SPayload::channel,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewPageEventC2SPayload::jsonPayload,
                        WebviewPageEventC2SPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // displayMode: 0 = GUI, 1 = HUD
    public record OpenWebS2CPayload(int protocolVersion, int displayMode, String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenWebS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(OPEN_WEB_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWebS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, OpenWebS2CPayload::protocolVersion,
                        ByteBufCodecs.VAR_INT, OpenWebS2CPayload::displayMode,
                        ByteBufCodecs.stringUtf8(WebviewProtocol.MAX_URL_LENGTH), OpenWebS2CPayload::url,
                        OpenWebS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record WebUIMainMenuPayload(String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebUIMainMenuPayload> TYPE =
                new CustomPacketPayload.Type<>(MAIN_MENU_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebUIMainMenuPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(WebviewProtocol.MAX_URL_LENGTH), WebUIMainMenuPayload::url,
                        WebUIMainMenuPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: sets (or clears) the entity context for the currently open GUI.
    public record WebviewEntityContextS2CPayload(String entityJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEntityContextS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ENTITY_CONTEXT_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEntityContextS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewEntityContextS2CPayload::entityJson,
                        WebviewEntityContextS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: the page that replaces the vanilla death screen, plus what killed the player.
    // The url arrives on join and on config reload with an empty info field so the client
    // already knows whether to suppress the vanilla screen before anyone dies; deciding
    // that at death time would race the vanilla death packet.
    public record WebviewDeathS2CPayload(String url, String infoJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewDeathS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(DEATH_SCREEN_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewDeathS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(WebviewProtocol.MAX_URL_LENGTH),
                        WebviewDeathS2CPayload::url,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewDeathS2CPayload::infoJson,
                        WebviewDeathS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: the server's key combinations and the page each opens, as one JSON array
    // (land.webgui.common.KeyMenus), sent on join and after a reload. The client has no list
    // of its own: an empty array clears what an earlier one set.
    public record WebviewKeyMenusS2CPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewKeyMenusS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(KEY_MENUS_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewKeyMenusS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_KEY_MENUS_LENGTH), WebviewKeyMenusS2CPayload::json,
                        WebviewKeyMenusS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: what the server is running, sent the moment a player joins. The client cannot
    // otherwise tell a server that speaks an older WebGUI protocol from one that speaks
    // none at all, and the loaders disagree about what happens then: NeoForge refuses the
    // connection with a message about its own version, Fabric lets the player in and
    // silently drops every packet. This says plainly who is running what.
    public record WebviewHelloS2CPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewHelloS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(HELLO_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewHelloS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WebviewHelloS2CPayload::protocolVersion,
                        ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), WebviewHelloS2CPayload::modVersion,
                        WebviewHelloS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: the list of pages this server ships, sent on join and after a reload. Paths
    // with hashes rather than content: a client that already holds a file from an earlier
    // session asks for nothing, and the same hash can be cached forever because changing
    // the file changes the name.
    public record WebviewAssetManifestS2CPayload(String revision, String manifest) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewAssetManifestS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ASSET_MANIFEST_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewAssetManifestS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), WebviewAssetManifestS2CPayload::revision,
                        ByteBufCodecs.stringUtf8(MAX_MANIFEST_LENGTH), WebviewAssetManifestS2CPayload::manifest,
                        WebviewAssetManifestS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // C2S: the client wants one file from the manifest.
    public record WebviewAssetRequestC2SPayload(String path) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewAssetRequestC2SPayload> TYPE =
                new CustomPacketPayload.Type<>(ASSET_REQUEST_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewAssetRequestC2SPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_ASSET_PATH_LENGTH), WebviewAssetRequestC2SPayload::path,
                        WebviewAssetRequestC2SPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: one slice of a requested file. A chunkCount of zero means the server has no
    // such file - the client is waiting on a browser request and needs an answer either
    // way, or the page hangs.
    public record WebviewAssetChunkS2CPayload(String path, int chunkIndex, int chunkCount, byte[] bytes)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewAssetChunkS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ASSET_CHUNK_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewAssetChunkS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_ASSET_PATH_LENGTH), WebviewAssetChunkS2CPayload::path,
                        ByteBufCodecs.VAR_INT, WebviewAssetChunkS2CPayload::chunkIndex,
                        ByteBufCodecs.VAR_INT, WebviewAssetChunkS2CPayload::chunkCount,
                        ByteBufCodecs.byteArray(ASSET_CHUNK_BYTES), WebviewAssetChunkS2CPayload::bytes,
                        WebviewAssetChunkS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // S2C: newline-joined origins whose pages may run commands as the player.
    public record WebviewTrustedOriginsS2CPayload(String origins) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewTrustedOriginsS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(TRUSTED_ORIGINS_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewTrustedOriginsS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewTrustedOriginsS2CPayload::origins,
                        WebviewTrustedOriginsS2CPayload::new);
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    //? }
}
