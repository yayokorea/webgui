package land.webgui.common;

import land.webgui.common.WebviewWire.AssetChunk;
import land.webgui.common.WebviewWire.AssetManifest;
import land.webgui.common.WebviewWire.AssetRequest;
import land.webgui.common.WebviewWire.DeathScreen;
import land.webgui.common.WebviewWire.EmitToPage;
import land.webgui.common.WebviewWire.EntityContext;
import land.webgui.common.WebviewWire.Hello;
import land.webgui.common.WebviewWire.OpenWeb;
import land.webgui.common.WebviewWire.PageEvent;
import land.webgui.common.WebviewWire.Reader;
import land.webgui.common.WebviewWire.SetMainMenu;
import land.webgui.common.WebviewWire.TrustedOrigins;
import land.webgui.common.WebviewWire.Writer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import static land.webgui.common.WebviewProtocol.ASSET_CHUNK_BYTES;
import static land.webgui.common.WebviewProtocol.MAX_ASSET_PATH_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_EVENT_DATA_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_EVENT_NAME_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_MANIFEST_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_URL_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_VERSION_LENGTH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebviewWireTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    private static byte[] varInt(int value) {
        return new Writer().writeVarInt(value).toByteArray();
    }

    /** The mixed-script, multi-byte-per-character text real servers put in names and URLs. */
    private static final String MULTIBYTE = "héllo wörld — 日本語 😀";

    // --- VarInt: the vectors Minecraft documents ---

    @Test
    void varIntEncodesLikeTheGame() {
        assertArrayEquals(bytes(0x00), varInt(0));
        assertArrayEquals(bytes(0x01), varInt(1));
        assertArrayEquals(bytes(0x7f), varInt(127));
        assertArrayEquals(bytes(0x80, 0x01), varInt(128));
        assertArrayEquals(bytes(0xff, 0x01), varInt(255));
        assertArrayEquals(bytes(0xdd, 0xc7, 0x01), varInt(25565));
        assertArrayEquals(bytes(0xff, 0xff, 0x7f), varInt(2097151));
        assertArrayEquals(bytes(0xff, 0xff, 0xff, 0xff, 0x07), varInt(Integer.MAX_VALUE));
        assertArrayEquals(bytes(0xff, 0xff, 0xff, 0xff, 0x0f), varInt(-1));
        assertArrayEquals(bytes(0x80, 0x80, 0x80, 0x80, 0x08), varInt(Integer.MIN_VALUE));
    }

    @Test
    void varIntRoundTrips() {
        int[] samples = {0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456,
                Integer.MAX_VALUE, -1, -128, Integer.MIN_VALUE};
        for (int v : samples) {
            assertEquals(v, new Reader(varInt(v)).readVarInt(), "value " + v);
        }
    }

    @Test
    void varIntLongerThanFiveBytesIsRejected() {
        assertThrows(WireException.class, () -> new Reader(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01)).readVarInt());
        assertThrows(WireException.class, () -> new Reader(bytes(0xff, 0xff, 0xff, 0xff, 0xff)).readVarInt());
    }

    @Test
    void truncatedVarIntIsRejected() {
        assertThrows(WireException.class, () -> new Reader(new byte[0]).readVarInt());
        assertThrows(WireException.class, () -> new Reader(bytes(0x80)).readVarInt());
    }

    // --- Strings ---

    @Test
    void stringIsLengthPrefixedUtf8() {
        assertArrayEquals(bytes(0x03, 'a', 'b', 'c'), new Writer().writeString("abc", 10).toByteArray());
        assertArrayEquals(bytes(0x00), new Writer().writeString("", 10).toByteArray());
        // é is two bytes, so the prefix counts bytes and not characters.
        assertArrayEquals(bytes(0x02, 0xc3, 0xa9), new Writer().writeString("é", 10).toByteArray());
    }

    @Test
    void multiByteStringsRoundTrip() {
        byte[] b = new Writer().writeString(MULTIBYTE, 100).toByteArray();
        assertEquals(MULTIBYTE, new Reader(b).readString(100));
    }

    @Test
    void stringLimitCountsCharactersOnWrite() {
        String atMax = "é".repeat(5);
        assertEquals(atMax, new Reader(new Writer().writeString(atMax, 5).toByteArray()).readString(5));
        assertThrows(WireException.class, () -> new Writer().writeString("é".repeat(6), 5));
        // An emoji is two chars (a surrogate pair), so it counts twice.
        assertThrows(WireException.class, () -> new Writer().writeString("😀", 1));
        new Writer().writeString("😀", 2);
    }

    @Test
    void stringLimitIsEnforcedOnRead() {
        byte[] ok = new Writer().writeString("abcde", 5).toByteArray();
        assertEquals("abcde", new Reader(ok).readString(5));
        assertThrows(WireException.class, () -> new Reader(ok).readString(4));
    }

    @Test
    void readRejectsDecodedStringOverTheCharLimitEvenWithinByteBudget() {
        // 4 chars encoded in 8 bytes: inside max*3 (3 chars -> 9 bytes), outside max chars.
        byte[] b = new Writer().writeString("日本語x", 10).toByteArray();
        assertThrows(WireException.class, () -> new Reader(b).readString(3));
    }

    @Test
    void readRejectsEncodedLengthOverThreeBytesPerChar() {
        // Claims 16 bytes for a 5-char limit (15 allowed); must fail before reading any.
        byte[] b = new byte[17];
        b[0] = 16;
        assertThrows(WireException.class, () -> new Reader(b).readString(5));
    }

    @Test
    void readRejectsNegativeStringLength() {
        assertThrows(WireException.class, () -> new Reader(varInt(-1)).readString(100));
        assertThrows(WireException.class, () -> new Reader(varInt(Integer.MIN_VALUE)).readString(100));
    }

    @Test
    void readRejectsHugeStringLengthWithoutAllocating() {
        // 2 GiB claimed, three bytes present: has to be a clean failure, not an OutOfMemoryError.
        byte[] b = varInt(Integer.MAX_VALUE);
        assertThrows(WireException.class, () -> new Reader(b).readString(Integer.MAX_VALUE / 3));
        assertThrows(WireException.class, () -> new Reader(b).readString(MAX_EVENT_DATA_LENGTH));
    }

    @Test
    void readRejectsStringTruncatedByTheEndOfInput() {
        byte[] full = new Writer().writeString("hello", 10).toByteArray();
        assertThrows(WireException.class, () -> new Reader(Arrays.copyOf(full, full.length - 1)).readString(10));
        assertThrows(WireException.class, () -> new Reader(new byte[0]).readString(10));
    }

    @Test
    void malformedUtf8DecodesLeniently() {
        // The game replaces bad bytes rather than failing, so decoding must too, or a
        // client could send something the server drops that a mod server would accept.
        String s = new Reader(bytes(0x02, 0xc3, 0x28)).readString(10);
        assertEquals("�(", s);
    }

    @Test
    void nullStringIsRejectedOnWrite() {
        assertThrows(WireException.class, () -> new Writer().writeString(null, 10));
    }

    // --- Byte arrays ---

    @Test
    void byteArrayIsLengthPrefixed() {
        assertArrayEquals(bytes(0x03, 1, 2, 3), new Writer().writeByteArray(bytes(1, 2, 3), 10).toByteArray());
        assertArrayEquals(bytes(0x00), new Writer().writeByteArray(new byte[0], 10).toByteArray());
    }

    @Test
    void byteArrayLimitIsEnforcedBothWays() {
        byte[] atMax = new byte[10];
        byte[] encoded = new Writer().writeByteArray(atMax, 10).toByteArray();
        assertArrayEquals(atMax, new Reader(encoded).readByteArray(10));
        assertThrows(WireException.class, () -> new Writer().writeByteArray(new byte[11], 10));
        assertThrows(WireException.class, () -> new Reader(encoded).readByteArray(9));
    }

    @Test
    void readRejectsNegativeAndHugeByteArrayLengths() {
        assertThrows(WireException.class, () -> new Reader(varInt(-1)).readByteArray(100));
        assertThrows(WireException.class, () -> new Reader(varInt(Integer.MAX_VALUE)).readByteArray(100));
        // Within the limit but not actually there.
        assertThrows(WireException.class, () -> new Reader(varInt(50)).readByteArray(100));
    }

    // --- Round trips of every payload ---

    @Test
    void openWebRoundTrips() {
        OpenWeb p = new OpenWeb(1, WebviewProtocol.MODE_HUD, "https://example.org/" + MULTIBYTE);
        assertEquals(p, WebviewWire.decodeOpenWeb(WebviewWire.encodeOpenWeb(p)));
    }

    @Test
    void openWebHasTheDocumentedLayout() {
        assertArrayEquals(bytes(1, 0, 3, 'a', 'b', 'c'), WebviewWire.encodeOpenWeb(new OpenWeb(1, 0, "abc")));
    }

    @Test
    void setMainMenuRoundTrips() {
        SetMainMenu p = new SetMainMenu("https://example.org/menu");
        assertEquals(p, WebviewWire.decodeSetMainMenu(WebviewWire.encodeSetMainMenu(p)));
        SetMainMenu empty = new SetMainMenu("");
        assertEquals(empty, WebviewWire.decodeSetMainMenu(WebviewWire.encodeSetMainMenu(empty)));
    }

    @Test
    void emitToPageRoundTrips() {
        EmitToPage p = new EmitToPage("shop:update", "{\"gold\":12,\"name\":\"" + MULTIBYTE + "\"}");
        assertEquals(p, WebviewWire.decodeEmitToPage(WebviewWire.encodeEmitToPage(p)));
    }

    @Test
    void entityContextRoundTrips() {
        EntityContext p = new EntityContext("null");
        assertEquals(p, WebviewWire.decodeEntityContext(WebviewWire.encodeEntityContext(p)));
    }

    @Test
    void trustedOriginsRoundTrips() {
        TrustedOrigins p = new TrustedOrigins("https://a.example\nhttps://b.example:8443");
        assertEquals(p, WebviewWire.decodeTrustedOrigins(WebviewWire.encodeTrustedOrigins(p)));
    }

    @Test
    void deathScreenRoundTrips() {
        DeathScreen p = new DeathScreen("https://example.org/death", "{\"cause\":\"fall\"}");
        assertEquals(p, WebviewWire.decodeDeathScreen(WebviewWire.encodeDeathScreen(p)));
        DeathScreen noInfo = new DeathScreen("https://example.org/death", "");
        assertEquals(noInfo, WebviewWire.decodeDeathScreen(WebviewWire.encodeDeathScreen(noInfo)));
    }

    @Test
    void helloRoundTrips() {
        Hello p = new Hello(WebviewProtocol.PROTOCOL_VERSION, "1.8.1+mc1.21.11");
        assertEquals(p, WebviewWire.decodeHello(WebviewWire.encodeHello(p)));
    }

    @Test
    void assetManifestRoundTrips() {
        AssetManifest p = new AssetManifest("abc123", "index.html\t1f2e\t120\nlogo.png\t9a8b\t4096\n");
        assertEquals(p, WebviewWire.decodeAssetManifest(WebviewWire.encodeAssetManifest(p)));
    }

    @Test
    void assetChunkRoundTrips() {
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        AssetChunk p = new AssetChunk("pages/index.html", 3, 9, data);
        AssetChunk decoded = WebviewWire.decodeAssetChunk(WebviewWire.encodeAssetChunk(p));
        assertEquals(p, decoded);
        assertArrayEquals(data, decoded.bytes());
    }

    @Test
    void emptyAssetChunkMeansNoSuchFile() {
        AssetChunk p = new AssetChunk("missing.html", 0, 0, new byte[0]);
        assertEquals(p, WebviewWire.decodeAssetChunk(WebviewWire.encodeAssetChunk(p)));
    }

    @Test
    void pageEventRoundTrips() {
        PageEvent p = new PageEvent("buy", "{\"item\":\"" + MULTIBYTE + "\"}");
        assertEquals(p, WebviewWire.decodePageEvent(WebviewWire.encodePageEvent(p)));
    }

    @Test
    void assetRequestRoundTrips() {
        AssetRequest p = new AssetRequest("pages/index.html");
        assertEquals(p, WebviewWire.decodeAssetRequest(WebviewWire.encodeAssetRequest(p)));
    }

    @Test
    void assetChunkEqualityLooksAtTheBytes() {
        AssetChunk a = new AssetChunk("p", 0, 1, bytes(1, 2));
        assertEquals(a, new AssetChunk("p", 0, 1, bytes(1, 2)));
        assertEquals(a.hashCode(), new AssetChunk("p", 0, 1, bytes(1, 2)).hashCode());
        assertNotEquals(a, new AssetChunk("p", 0, 1, bytes(1, 3)));
    }

    // --- Boundaries: every string field at its limit, and one past ---

    private record StringField(String name, int max, Function<String, byte[]> encode, Function<byte[], Object> decode) {}

    private static StringField[] stringFields() {
        return new StringField[] {
                new StringField("openWeb.url", MAX_URL_LENGTH,
                        s -> WebviewWire.encodeOpenWeb(new OpenWeb(1, 0, s)), WebviewWire::decodeOpenWeb),
                new StringField("setMainMenu.url", MAX_URL_LENGTH,
                        s -> WebviewWire.encodeSetMainMenu(new SetMainMenu(s)), WebviewWire::decodeSetMainMenu),
                new StringField("emitToPage.eventName", MAX_EVENT_NAME_LENGTH,
                        s -> WebviewWire.encodeEmitToPage(new EmitToPage(s, "")), WebviewWire::decodeEmitToPage),
                new StringField("emitToPage.json", MAX_EVENT_DATA_LENGTH,
                        s -> WebviewWire.encodeEmitToPage(new EmitToPage("e", s)), WebviewWire::decodeEmitToPage),
                new StringField("entityContext.json", MAX_EVENT_DATA_LENGTH,
                        s -> WebviewWire.encodeEntityContext(new EntityContext(s)), WebviewWire::decodeEntityContext),
                new StringField("trustedOrigins", MAX_EVENT_DATA_LENGTH,
                        s -> WebviewWire.encodeTrustedOrigins(new TrustedOrigins(s)), WebviewWire::decodeTrustedOrigins),
                new StringField("deathScreen.url", MAX_URL_LENGTH,
                        s -> WebviewWire.encodeDeathScreen(new DeathScreen(s, "")), WebviewWire::decodeDeathScreen),
                new StringField("deathScreen.info", MAX_EVENT_DATA_LENGTH,
                        s -> WebviewWire.encodeDeathScreen(new DeathScreen("", s)), WebviewWire::decodeDeathScreen),
                new StringField("hello.version", MAX_VERSION_LENGTH,
                        s -> WebviewWire.encodeHello(new Hello(1, s)), WebviewWire::decodeHello),
                new StringField("assetManifest.revision", MAX_VERSION_LENGTH,
                        s -> WebviewWire.encodeAssetManifest(new AssetManifest(s, "")), WebviewWire::decodeAssetManifest),
                new StringField("assetManifest.manifest", MAX_MANIFEST_LENGTH,
                        s -> WebviewWire.encodeAssetManifest(new AssetManifest("r", s)), WebviewWire::decodeAssetManifest),
                new StringField("assetChunk.path", MAX_ASSET_PATH_LENGTH,
                        s -> WebviewWire.encodeAssetChunk(new AssetChunk(s, 0, 1, new byte[0])), WebviewWire::decodeAssetChunk),
                new StringField("pageEvent.channel", MAX_EVENT_NAME_LENGTH,
                        s -> WebviewWire.encodePageEvent(new PageEvent(s, "")), WebviewWire::decodePageEvent),
                new StringField("pageEvent.json", MAX_EVENT_DATA_LENGTH,
                        s -> WebviewWire.encodePageEvent(new PageEvent("c", s)), WebviewWire::decodePageEvent),
                new StringField("assetRequest.path", MAX_ASSET_PATH_LENGTH,
                        s -> WebviewWire.encodeAssetRequest(new AssetRequest(s)), WebviewWire::decodeAssetRequest),
        };
    }

    @Test
    void everyStringFieldAcceptsExactlyItsLimit() {
        for (StringField f : stringFields()) {
            for (String unit : new String[] {"a", "é", "日"}) {
                String atMax = unit.repeat(f.max());
                byte[] encoded = f.encode().apply(atMax);
                f.decode().apply(encoded); // does not throw
                assertThrows(WireException.class, () -> f.encode().apply(unit.repeat(f.max() + 1)),
                        f.name() + " one past the limit, " + unit);
            }
        }
    }

    @Test
    void everyDecoderRejectsAStringOneOverItsLimit() {
        for (StringField f : stringFields()) {
            // Written by hand past the limit, since the writer would refuse to. The field
            // is located by comparing an empty and a one-character encoding: the first byte
            // that differs is its length prefix.
            byte[] over = "a".repeat(f.max() + 1).getBytes(StandardCharsets.UTF_8);
            byte[] empty = f.encode().apply("");
            int at = firstDifference(empty, f.encode().apply("a"));
            byte[] payload = concat(Arrays.copyOf(empty, at), varInt(over.length), over,
                    Arrays.copyOfRange(empty, at + 1, empty.length));
            assertThrows(WireException.class, () -> f.decode().apply(payload), f.name());
        }
    }

    private static int firstDifference(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        throw new AssertionError("no difference");
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    @Test
    void assetChunkBytesAcceptExactlyTheChunkSize() {
        AssetChunk atMax = new AssetChunk("p", 0, 1, new byte[ASSET_CHUNK_BYTES]);
        assertEquals(atMax, WebviewWire.decodeAssetChunk(WebviewWire.encodeAssetChunk(atMax)));
        assertThrows(WireException.class,
                () -> WebviewWire.encodeAssetChunk(new AssetChunk("p", 0, 1, new byte[ASSET_CHUNK_BYTES + 1])));
        byte[] over = new Writer().writeString("p", 10).writeVarInt(0).writeVarInt(1)
                .writeVarInt(ASSET_CHUNK_BYTES + 1).toByteArray();
        assertThrows(WireException.class, () -> WebviewWire.decodeAssetChunk(over));
    }

    // --- Malformed input to every decoder ---

    private static byte[][] samplePayloads() {
        return new byte[][] {
                WebviewWire.encodeOpenWeb(new OpenWeb(1, 0, "https://example.org")),
                WebviewWire.encodeSetMainMenu(new SetMainMenu("https://example.org")),
                WebviewWire.encodeEmitToPage(new EmitToPage("name", "{}")),
                WebviewWire.encodeEntityContext(new EntityContext("{}")),
                WebviewWire.encodeTrustedOrigins(new TrustedOrigins("https://example.org")),
                WebviewWire.encodeDeathScreen(new DeathScreen("https://example.org", "{}")),
                WebviewWire.encodeHello(new Hello(1, "1.8.1")),
                WebviewWire.encodeAssetManifest(new AssetManifest("rev", "a\tb\t1\n")),
                WebviewWire.encodeAssetChunk(new AssetChunk("p", 0, 1, bytes(1, 2, 3))),
                WebviewWire.encodePageEvent(new PageEvent("c", "{}")),
                WebviewWire.encodeAssetRequest(new AssetRequest("index.html")),
        };
    }

    @SuppressWarnings("unchecked")
    private static Function<byte[], Object>[] decoders() {
        return new Function[] {
                (Function<byte[], Object>) WebviewWire::decodeOpenWeb,
                (Function<byte[], Object>) WebviewWire::decodeSetMainMenu,
                (Function<byte[], Object>) WebviewWire::decodeEmitToPage,
                (Function<byte[], Object>) WebviewWire::decodeEntityContext,
                (Function<byte[], Object>) WebviewWire::decodeTrustedOrigins,
                (Function<byte[], Object>) WebviewWire::decodeDeathScreen,
                (Function<byte[], Object>) WebviewWire::decodeHello,
                (Function<byte[], Object>) WebviewWire::decodeAssetManifest,
                (Function<byte[], Object>) WebviewWire::decodeAssetChunk,
                (Function<byte[], Object>) WebviewWire::decodePageEvent,
                (Function<byte[], Object>) WebviewWire::decodeAssetRequest,
        };
    }

    @Test
    void everyDecoderRejectsEveryTruncation() {
        byte[][] payloads = samplePayloads();
        Function<byte[], Object>[] decoders = decoders();
        for (int i = 0; i < payloads.length; i++) {
            for (int cut = 0; cut < payloads[i].length; cut++) {
                byte[] truncated = Arrays.copyOf(payloads[i], cut);
                int payload = i;
                assertThrows(WireException.class, () -> decoders[payload].apply(truncated),
                        "payload " + i + " cut to " + cut + " bytes");
            }
        }
    }

    @Test
    void everyDecoderRejectsTrailingBytes() {
        byte[][] payloads = samplePayloads();
        Function<byte[], Object>[] decoders = decoders();
        for (int i = 0; i < payloads.length; i++) {
            byte[] extended = Arrays.copyOf(payloads[i], payloads[i].length + 1);
            int payload = i;
            assertThrows(WireException.class, () -> decoders[payload].apply(extended), "payload " + i);
        }
    }

    @Test
    void everyDecoderOnlyEverThrowsWireException() {
        // Attacker-controlled bytes: nothing but a clean WireException may come out, for
        // any prefix pattern, so a caller needs exactly one catch.
        java.util.Random random = new java.util.Random(1234);
        Function<byte[], Object>[] decoders = decoders();
        for (int round = 0; round < 3000; round++) {
            byte[] junk = new byte[random.nextInt(24)];
            random.nextBytes(junk);
            if (round % 3 == 0 && junk.length > 0) {
                junk[0] = (byte) 0xff; // a VarInt that wants to be huge
            }
            for (Function<byte[], Object> decoder : decoders) {
                try {
                    decoder.apply(junk);
                } catch (WireException expected) {
                    // fine
                }
            }
        }
    }

    @Test
    void nullPayloadIsRejected() {
        assertThrows(WireException.class, () -> WebviewWire.decodeHello(null));
    }
}
