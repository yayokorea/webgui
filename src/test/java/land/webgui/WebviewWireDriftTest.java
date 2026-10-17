package land.webgui;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import land.webgui.common.WebviewProtocol;
import land.webgui.common.WebviewWire;
import land.webgui.common.WebviewWire.AssetChunk;
import land.webgui.common.WebviewWire.AssetManifest;
import land.webgui.common.WebviewWire.AssetRequest;
import land.webgui.common.WebviewWire.DeathScreen;
import land.webgui.common.WebviewWire.EmitToPage;
import land.webgui.common.WebviewWire.EntityContext;
import land.webgui.common.WebviewWire.Hello;
import land.webgui.common.WebviewWire.OpenWeb;
import land.webgui.common.WebviewWire.PageEvent;
import land.webgui.common.WebviewWire.SetMainMenu;
import land.webgui.common.WebviewWire.TrustedOrigins;
import org.junit.jupiter.api.Test;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
//? } else {
/*import net.minecraft.network.FriendlyByteBuf;
*///? }

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drift test: {@link WebviewWire} must produce, and understand, exactly the bytes the
 * game's own codecs do.
 *
 * The Paper plugin talks to the same clients through {@code WebviewWire} instead of these
 * codecs. Nothing else ties the two together, so this runs every payload through both on
 * every Minecraft version this mod builds for: a changed field order, limit or primitive
 * on either side fails here, rather than as a client that silently ignores the server.
 *
 * Needs no running game. A codec only ever touches VarInts, strings and byte arrays, never
 * a registry, so the buffer is built without one.
 */
class WebviewWireDriftTest {

    private static final String UNICODE = "héllo wörld — 日本語 😀";

    /** One payload as the game sees it. {@code T} is the matching {@link WebviewWire} record. */
    private interface Game<T> {
        byte[] encode(T value);

        T decode(byte[] bytes);
    }

    private static <T> void agree(
            List<T> values,
            Function<T, byte[]> wireEncode,
            Function<byte[], T> wireDecode,
            Game<T> game) {
        for (T value : values) {
            byte[] fromWire = wireEncode.apply(value);
            byte[] fromGame = game.encode(value);
            assertArrayEquals(fromGame, fromWire, "bytes differ for " + value);
            assertEquals(value, game.decode(fromWire), "the game misreads what WebviewWire wrote");
            assertEquals(value, wireDecode.apply(fromGame), "WebviewWire misreads what the game wrote");
        }
    }

    private static byte[] bytes(int length) {
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) {
            b[i] = (byte) (i * 31);
        }
        return b;
    }

    // Strings at the interesting sizes: empty, plain, multi-byte, and exactly at the limit.
    private static List<String> strings(int max) {
        return List.of("", "a", "https://example.org/a?b=c&d=e", UNICODE, "x".repeat(max), "日".repeat(max));
    }

    private static List<Integer> varInts() {
        return List.of(0, 1, 127, 128, 255, 300, 16384, 2097151, Integer.MAX_VALUE, -1, Integer.MIN_VALUE);
    }

    // --- the payloads ---

    @Test
    void openWeb() {
        agree(List.of(new OpenWeb(1, 0, ""), new OpenWeb(1, 1, UNICODE), new OpenWeb(300, 1, "u".repeat(WebviewProtocol.MAX_URL_LENGTH)),
                        new OpenWeb(-1, Integer.MAX_VALUE, "日".repeat(WebviewProtocol.MAX_URL_LENGTH))),
                WebviewWire::encodeOpenWeb, WebviewWire::decodeOpenWeb, gameOpenWeb());
    }

    @Test
    void setMainMenu() {
        agree(strings(WebviewProtocol.MAX_URL_LENGTH).stream().map(SetMainMenu::new).toList(),
                WebviewWire::encodeSetMainMenu, WebviewWire::decodeSetMainMenu, gameSetMainMenu());
    }

    @Test
    void emitToPage() {
        agree(List.of(new EmitToPage("", ""), new EmitToPage("shop:update", "{\"a\":1}"),
                        new EmitToPage(UNICODE, UNICODE),
                        new EmitToPage("n".repeat(WebviewProtocol.MAX_EVENT_NAME_LENGTH), "d".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH)),
                        new EmitToPage("日".repeat(WebviewProtocol.MAX_EVENT_NAME_LENGTH), "日".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH))),
                WebviewWire::encodeEmitToPage, WebviewWire::decodeEmitToPage, gameEmitToPage());
    }

    @Test
    void entityContext() {
        agree(strings(WebviewProtocol.MAX_EVENT_DATA_LENGTH).stream().map(EntityContext::new).toList(),
                WebviewWire::encodeEntityContext, WebviewWire::decodeEntityContext, gameEntityContext());
    }

    @Test
    void trustedOrigins() {
        agree(strings(WebviewProtocol.MAX_EVENT_DATA_LENGTH).stream().map(TrustedOrigins::new).toList(),
                WebviewWire::encodeTrustedOrigins, WebviewWire::decodeTrustedOrigins, gameTrustedOrigins());
    }

    @Test
    void deathScreen() {
        agree(List.of(new DeathScreen("", ""), new DeathScreen("https://example.org/death", "{\"cause\":\"fall\"}"),
                        new DeathScreen(UNICODE, UNICODE),
                        new DeathScreen("u".repeat(WebviewProtocol.MAX_URL_LENGTH), "d".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH)),
                        new DeathScreen("日".repeat(WebviewProtocol.MAX_URL_LENGTH), "日".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH))),
                WebviewWire::encodeDeathScreen, WebviewWire::decodeDeathScreen, gameDeathScreen());
    }

    @Test
    void hello() {
        agree(varInts().stream().map(v -> new Hello(v, "1.8.1+mc1.21.11")).toList(),
                WebviewWire::encodeHello, WebviewWire::decodeHello, gameHello());
        agree(strings(WebviewProtocol.MAX_VERSION_LENGTH).stream().map(s -> new Hello(1, s)).toList(),
                WebviewWire::encodeHello, WebviewWire::decodeHello, gameHello());
    }

    @Test
    void assetManifest() {
        agree(List.of(new AssetManifest("", ""), new AssetManifest("abc123", "index.html\t1f2e\t120\n"),
                        new AssetManifest(UNICODE, UNICODE),
                        new AssetManifest("r".repeat(WebviewProtocol.MAX_VERSION_LENGTH), "m".repeat(WebviewProtocol.MAX_MANIFEST_LENGTH)),
                        new AssetManifest("日".repeat(WebviewProtocol.MAX_VERSION_LENGTH), "日".repeat(WebviewProtocol.MAX_MANIFEST_LENGTH))),
                WebviewWire::encodeAssetManifest, WebviewWire::decodeAssetManifest, gameAssetManifest());
    }

    @Test
    void assetChunk() {
        agree(List.of(new AssetChunk("missing.html", 0, 0, new byte[0]),
                        new AssetChunk("pages/index.html", 3, 9, bytes(1000)),
                        new AssetChunk(UNICODE, 2097151, Integer.MAX_VALUE, bytes(1)),
                        new AssetChunk("p".repeat(WebviewProtocol.MAX_ASSET_PATH_LENGTH), 127, 128, bytes(WebviewProtocol.ASSET_CHUNK_BYTES)),
                        new AssetChunk("日".repeat(WebviewProtocol.MAX_ASSET_PATH_LENGTH), -1, -1, bytes(300))),
                WebviewWire::encodeAssetChunk, WebviewWire::decodeAssetChunk, gameAssetChunk());
    }

    @Test
    void pageEvent() {
        agree(List.of(new PageEvent("", ""), new PageEvent("buy", "{\"item\":\"apple\"}"),
                        new PageEvent(UNICODE, UNICODE),
                        new PageEvent("c".repeat(WebviewProtocol.MAX_EVENT_NAME_LENGTH), "d".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH)),
                        new PageEvent("日".repeat(WebviewProtocol.MAX_EVENT_NAME_LENGTH), "日".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH))),
                WebviewWire::encodePageEvent, WebviewWire::decodePageEvent, gamePageEvent());
    }

    @Test
    void assetRequest() {
        agree(strings(WebviewProtocol.MAX_ASSET_PATH_LENGTH).stream().map(AssetRequest::new).toList(),
                WebviewWire::encodeAssetRequest, WebviewWire::decodeAssetRequest, gameAssetRequest());
    }

    // --- the game's side of each, per Minecraft version ---

    //? if >=1.20.5 {
    // From 1.20.5 every payload carries its own codec, so this is the mod's real code.
    // The field is CODEC on Fabric and STREAM_CODEC on NeoForge.
    @SuppressWarnings("unchecked")
    private static <P> Game<P> codec(Class<?> payload) {
        Object codec;
        try {
            try {
                codec = payload.getField("CODEC").get(null);
            } catch (NoSuchFieldException e) {
                codec = payload.getField("STREAM_CODEC").get(null);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no codec on " + payload, e);
        }
        StreamCodec<RegistryFriendlyByteBuf, P> c = (StreamCodec<RegistryFriendlyByteBuf, P>) codec;
        return new Game<>() {
            @Override
            public byte[] encode(P value) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
                c.encode(buf, value);
                return drain(buf);
            }

            @Override
            public P decode(byte[] bytes) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), null);
                P value = c.decode(buf);
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return value;
            }
        };
    }

    // Adapts the game's payload record to the WebviewWire record carrying the same fields.
    private static <T, P> Game<T> game(Class<P> payload, Function<T, P> toGame, Function<P, T> fromGame) {
        Game<P> raw = codec(payload);
        return new Game<>() {
            @Override
            public byte[] encode(T value) {
                return raw.encode(toGame.apply(value));
            }

            @Override
            public T decode(byte[] bytes) {
                return fromGame.apply(raw.decode(bytes));
            }
        };
    }

    private static Game<OpenWeb> gameOpenWeb() {
        return game(WebviewPayloads.OpenWebS2CPayload.class,
                w -> new WebviewPayloads.OpenWebS2CPayload(w.protocolVersion(), w.displayMode(), w.url()),
                p -> new OpenWeb(p.protocolVersion(), p.displayMode(), p.url()));
    }

    private static Game<SetMainMenu> gameSetMainMenu() {
        return game(WebviewPayloads.WebUIMainMenuPayload.class,
                w -> new WebviewPayloads.WebUIMainMenuPayload(w.url()),
                p -> new SetMainMenu(p.url()));
    }

    private static Game<EmitToPage> gameEmitToPage() {
        return game(WebviewPayloads.WebviewEmitS2CPayload.class,
                w -> new WebviewPayloads.WebviewEmitS2CPayload(w.eventName(), w.jsonPayload()),
                p -> new EmitToPage(p.eventName(), p.jsonPayload()));
    }

    private static Game<EntityContext> gameEntityContext() {
        return game(WebviewPayloads.WebviewEntityContextS2CPayload.class,
                w -> new WebviewPayloads.WebviewEntityContextS2CPayload(w.entityJson()),
                p -> new EntityContext(p.entityJson()));
    }

    private static Game<TrustedOrigins> gameTrustedOrigins() {
        return game(WebviewPayloads.WebviewTrustedOriginsS2CPayload.class,
                w -> new WebviewPayloads.WebviewTrustedOriginsS2CPayload(w.origins()),
                p -> new TrustedOrigins(p.origins()));
    }

    private static Game<DeathScreen> gameDeathScreen() {
        return game(WebviewPayloads.WebviewDeathS2CPayload.class,
                w -> new WebviewPayloads.WebviewDeathS2CPayload(w.url(), w.infoJson()),
                p -> new DeathScreen(p.url(), p.infoJson()));
    }

    private static Game<Hello> gameHello() {
        return game(WebviewPayloads.WebviewHelloS2CPayload.class,
                w -> new WebviewPayloads.WebviewHelloS2CPayload(w.protocolVersion(), w.modVersion()),
                p -> new Hello(p.protocolVersion(), p.modVersion()));
    }

    private static Game<AssetManifest> gameAssetManifest() {
        return game(WebviewPayloads.WebviewAssetManifestS2CPayload.class,
                w -> new WebviewPayloads.WebviewAssetManifestS2CPayload(w.revision(), w.manifest()),
                p -> new AssetManifest(p.revision(), p.manifest()));
    }

    private static Game<AssetChunk> gameAssetChunk() {
        return game(WebviewPayloads.WebviewAssetChunkS2CPayload.class,
                w -> new WebviewPayloads.WebviewAssetChunkS2CPayload(w.path(), w.chunkIndex(), w.chunkCount(), w.bytes()),
                p -> new AssetChunk(p.path(), p.chunkIndex(), p.chunkCount(), p.bytes()));
    }

    private static Game<PageEvent> gamePageEvent() {
        return game(WebviewPayloads.WebviewPageEventC2SPayload.class,
                w -> new WebviewPayloads.WebviewPageEventC2SPayload(w.channel(), w.jsonPayload()),
                p -> new PageEvent(p.channel(), p.jsonPayload()));
    }

    private static Game<AssetRequest> gameAssetRequest() {
        return game(WebviewPayloads.WebviewAssetRequestC2SPayload.class,
                w -> new WebviewPayloads.WebviewAssetRequestC2SPayload(w.path()),
                p -> new AssetRequest(p.path()));
    }
    //? } else {
    /*// 1.20.1 has no payload codecs: WebviewNetworking and WebGUIClient write and read
    // each packet field by field on a FriendlyByteBuf. These do the same, in the same order
    // as those, so what is compared here is the game's VarInt, string and byte-array
    // primitives; a reordered field in the networking code is not caught on this version.
    private static Game<OpenWeb> gameOpenWeb() {
        return new Game<>() {
            @Override
            public byte[] encode(OpenWeb v) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(v.protocolVersion());
                buf.writeVarInt(v.displayMode());
                buf.writeUtf(v.url(), WebviewProtocol.MAX_URL_LENGTH);
                return drain(buf);
            }

            @Override
            public OpenWeb decode(byte[] bytes) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                OpenWeb v = new OpenWeb(buf.readVarInt(), buf.readVarInt(), buf.readUtf(WebviewProtocol.MAX_URL_LENGTH));
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return v;
            }
        };
    }

    private static Game<SetMainMenu> gameSetMainMenu() {
        return single(WebviewProtocol.MAX_URL_LENGTH, SetMainMenu::url, SetMainMenu::new);
    }

    private static Game<EmitToPage> gameEmitToPage() {
        return pair(WebviewProtocol.MAX_EVENT_NAME_LENGTH, WebviewProtocol.MAX_EVENT_DATA_LENGTH,
                EmitToPage::eventName, EmitToPage::jsonPayload, EmitToPage::new);
    }

    private static Game<EntityContext> gameEntityContext() {
        return single(WebviewProtocol.MAX_EVENT_DATA_LENGTH, EntityContext::entityJson, EntityContext::new);
    }

    private static Game<TrustedOrigins> gameTrustedOrigins() {
        return single(WebviewProtocol.MAX_EVENT_DATA_LENGTH, TrustedOrigins::origins, TrustedOrigins::new);
    }

    private static Game<DeathScreen> gameDeathScreen() {
        return pair(WebviewProtocol.MAX_URL_LENGTH, WebviewProtocol.MAX_EVENT_DATA_LENGTH,
                DeathScreen::url, DeathScreen::infoJson, DeathScreen::new);
    }

    private static Game<Hello> gameHello() {
        return new Game<>() {
            @Override
            public byte[] encode(Hello v) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(v.protocolVersion());
                buf.writeUtf(v.modVersion(), WebviewProtocol.MAX_VERSION_LENGTH);
                return drain(buf);
            }

            @Override
            public Hello decode(byte[] bytes) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                Hello v = new Hello(buf.readVarInt(), buf.readUtf(WebviewProtocol.MAX_VERSION_LENGTH));
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return v;
            }
        };
    }

    private static Game<AssetManifest> gameAssetManifest() {
        return pair(WebviewProtocol.MAX_VERSION_LENGTH, WebviewProtocol.MAX_MANIFEST_LENGTH,
                AssetManifest::revision, AssetManifest::manifest, AssetManifest::new);
    }

    private static Game<AssetChunk> gameAssetChunk() {
        return new Game<>() {
            @Override
            public byte[] encode(AssetChunk v) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(v.path(), WebviewProtocol.MAX_ASSET_PATH_LENGTH);
                buf.writeVarInt(v.chunkIndex());
                buf.writeVarInt(v.chunkCount());
                buf.writeByteArray(v.bytes());
                return drain(buf);
            }

            @Override
            public AssetChunk decode(byte[] bytes) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                AssetChunk v = new AssetChunk(buf.readUtf(WebviewProtocol.MAX_ASSET_PATH_LENGTH),
                        buf.readVarInt(), buf.readVarInt(), buf.readByteArray(WebviewProtocol.ASSET_CHUNK_BYTES));
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return v;
            }
        };
    }

    private static Game<PageEvent> gamePageEvent() {
        return pair(WebviewProtocol.MAX_EVENT_NAME_LENGTH, WebviewProtocol.MAX_EVENT_DATA_LENGTH,
                PageEvent::channel, PageEvent::jsonPayload, PageEvent::new);
    }

    private static Game<AssetRequest> gameAssetRequest() {
        return single(WebviewProtocol.MAX_ASSET_PATH_LENGTH, AssetRequest::path, AssetRequest::new);
    }

    private static <T> Game<T> single(int max, Function<T, String> get, Function<String, T> make) {
        return new Game<>() {
            @Override
            public byte[] encode(T v) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(get.apply(v), max);
                return drain(buf);
            }

            @Override
            public T decode(byte[] bytes) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                T v = make.apply(buf.readUtf(max));
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return v;
            }
        };
    }

    private static <T> Game<T> pair(int max1, int max2, Function<T, String> get1, Function<T, String> get2,
                                    java.util.function.BiFunction<String, String, T> make) {
        return new Game<>() {
            @Override
            public byte[] encode(T v) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(get1.apply(v), max1);
                buf.writeUtf(get2.apply(v), max2);
                return drain(buf);
            }

            @Override
            public T decode(byte[] bytes) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                T v = make.apply(buf.readUtf(max1), buf.readUtf(max2));
                assertEquals(0, buf.readableBytes(), "the game left bytes unread");
                return v;
            }
        };
    }*/
    //? }

    private static byte[] drain(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }
}
