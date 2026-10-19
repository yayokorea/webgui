package land.webgui.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static land.webgui.common.WebviewProtocol.ASSET_CHUNK_BYTES;
import static land.webgui.common.WebviewProtocol.MAX_ASSET_PATH_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_EVENT_DATA_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_EVENT_NAME_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_KEY_MENUS_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_MANIFEST_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_URL_LENGTH;
import static land.webgui.common.WebviewProtocol.MAX_VERSION_LENGTH;

/**
 * The bytes of every WebGUI payload, written and read without Minecraft.
 *
 * The mod's own codecs come from the game (and differ per version); a server that is not
 * the game — a Paper plugin — has none of them and still has to produce exactly the bytes
 * a client expects. Every payload is a sequence of three primitives, all of which are
 * reproduced here the way Minecraft defines them:
 * <ul>
 *   <li>VarInt: 7 bits per byte, low bits first, high bit means "more"; a negative value
 *       always takes five bytes.</li>
 *   <li>String: a VarInt byte length, then UTF-8. Capped by <em>characters</em> on write
 *       and, on read, by at most three bytes per allowed character and then by the
 *       decoded character count.</li>
 *   <li>byte[]: a VarInt length, then the bytes.</li>
 * </ul>
 * A drift test in the mod build encodes the same values with the real game codecs and
 * compares bytes, so a change in Minecraft or in a payload's field order fails there
 * rather than at a player's client.
 *
 * Decoders take input from the network, so they never size an allocation from a length
 * they have not checked, reject anything left over after the last field (Minecraft
 * drops the connection for the same), and fail with {@link WireException} only.
 */
public final class WebviewWire {
    private WebviewWire() {}

    // --- S2C payloads (what a server sends) ---

    public record OpenWeb(int protocolVersion, int displayMode, String url) {}

    public record SetMainMenu(String url) {}

    public record EmitToPage(String eventName, String jsonPayload) {}

    public record EntityContext(String entityJson) {}

    public record TrustedOrigins(String origins) {}

    public record DeathScreen(String url, String infoJson) {}

    public record KeyMenuList(String json) {}

    public record Hello(int protocolVersion, String modVersion) {}

    public record AssetManifest(String revision, String manifest) {}

    public record AssetChunk(String path, int chunkIndex, int chunkCount, byte[] bytes) {
        // A record compares arrays by identity, which would make two equal chunks unequal.
        @Override
        public boolean equals(Object o) {
            return o instanceof AssetChunk c
                    && chunkIndex == c.chunkIndex
                    && chunkCount == c.chunkCount
                    && path.equals(c.path)
                    && Arrays.equals(bytes, c.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * path.hashCode() + chunkIndex) + chunkCount) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "AssetChunk[path=" + path + ", chunkIndex=" + chunkIndex
                    + ", chunkCount=" + chunkCount + ", bytes=" + bytes.length + " bytes]";
        }
    }

    // --- C2S payloads (what a client sends) ---

    public record PageEvent(String channel, String jsonPayload) {}

    public record AssetRequest(String path) {}

    // --- S2C encoders ---

    public static byte[] encodeOpenWeb(OpenWeb p) {
        return new Writer()
                .writeVarInt(p.protocolVersion())
                .writeVarInt(p.displayMode())
                .writeString(p.url(), MAX_URL_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeSetMainMenu(SetMainMenu p) {
        return new Writer().writeString(p.url(), MAX_URL_LENGTH).toByteArray();
    }

    public static byte[] encodeEmitToPage(EmitToPage p) {
        return new Writer()
                .writeString(p.eventName(), MAX_EVENT_NAME_LENGTH)
                .writeString(p.jsonPayload(), MAX_EVENT_DATA_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeEntityContext(EntityContext p) {
        return new Writer().writeString(p.entityJson(), MAX_EVENT_DATA_LENGTH).toByteArray();
    }

    public static byte[] encodeTrustedOrigins(TrustedOrigins p) {
        return new Writer().writeString(p.origins(), MAX_EVENT_DATA_LENGTH).toByteArray();
    }

    public static byte[] encodeDeathScreen(DeathScreen p) {
        return new Writer()
                .writeString(p.url(), MAX_URL_LENGTH)
                .writeString(p.infoJson(), MAX_EVENT_DATA_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeKeyMenus(KeyMenuList p) {
        return new Writer().writeString(p.json(), MAX_KEY_MENUS_LENGTH).toByteArray();
    }

    public static byte[] encodeHello(Hello p) {
        return new Writer()
                .writeVarInt(p.protocolVersion())
                .writeString(p.modVersion(), MAX_VERSION_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeAssetManifest(AssetManifest p) {
        return new Writer()
                .writeString(p.revision(), MAX_VERSION_LENGTH)
                .writeString(p.manifest(), MAX_MANIFEST_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeAssetChunk(AssetChunk p) {
        return new Writer()
                .writeString(p.path(), MAX_ASSET_PATH_LENGTH)
                .writeVarInt(p.chunkIndex())
                .writeVarInt(p.chunkCount())
                .writeByteArray(p.bytes(), ASSET_CHUNK_BYTES)
                .toByteArray();
    }

    // --- C2S encoders ---

    public static byte[] encodePageEvent(PageEvent p) {
        return new Writer()
                .writeString(p.channel(), MAX_EVENT_NAME_LENGTH)
                .writeString(p.jsonPayload(), MAX_EVENT_DATA_LENGTH)
                .toByteArray();
    }

    public static byte[] encodeAssetRequest(AssetRequest p) {
        return new Writer().writeString(p.path(), MAX_ASSET_PATH_LENGTH).toByteArray();
    }

    // --- S2C decoders ---

    public static OpenWeb decodeOpenWeb(byte[] data) {
        Reader r = new Reader(data);
        OpenWeb p = new OpenWeb(r.readVarInt(), r.readVarInt(), r.readString(MAX_URL_LENGTH));
        return r.finish(p);
    }

    public static SetMainMenu decodeSetMainMenu(byte[] data) {
        Reader r = new Reader(data);
        return r.finish(new SetMainMenu(r.readString(MAX_URL_LENGTH)));
    }

    public static EmitToPage decodeEmitToPage(byte[] data) {
        Reader r = new Reader(data);
        EmitToPage p = new EmitToPage(r.readString(MAX_EVENT_NAME_LENGTH), r.readString(MAX_EVENT_DATA_LENGTH));
        return r.finish(p);
    }

    public static EntityContext decodeEntityContext(byte[] data) {
        Reader r = new Reader(data);
        return r.finish(new EntityContext(r.readString(MAX_EVENT_DATA_LENGTH)));
    }

    public static TrustedOrigins decodeTrustedOrigins(byte[] data) {
        Reader r = new Reader(data);
        return r.finish(new TrustedOrigins(r.readString(MAX_EVENT_DATA_LENGTH)));
    }

    public static DeathScreen decodeDeathScreen(byte[] data) {
        Reader r = new Reader(data);
        DeathScreen p = new DeathScreen(r.readString(MAX_URL_LENGTH), r.readString(MAX_EVENT_DATA_LENGTH));
        return r.finish(p);
    }

    public static KeyMenuList decodeKeyMenus(byte[] data) {
        Reader r = new Reader(data);
        return r.finish(new KeyMenuList(r.readString(MAX_KEY_MENUS_LENGTH)));
    }

    public static Hello decodeHello(byte[] data) {
        Reader r = new Reader(data);
        Hello p = new Hello(r.readVarInt(), r.readString(MAX_VERSION_LENGTH));
        return r.finish(p);
    }

    public static AssetManifest decodeAssetManifest(byte[] data) {
        Reader r = new Reader(data);
        AssetManifest p = new AssetManifest(r.readString(MAX_VERSION_LENGTH), r.readString(MAX_MANIFEST_LENGTH));
        return r.finish(p);
    }

    public static AssetChunk decodeAssetChunk(byte[] data) {
        Reader r = new Reader(data);
        AssetChunk p = new AssetChunk(
                r.readString(MAX_ASSET_PATH_LENGTH),
                r.readVarInt(),
                r.readVarInt(),
                r.readByteArray(ASSET_CHUNK_BYTES));
        return r.finish(p);
    }

    // --- C2S decoders ---

    public static PageEvent decodePageEvent(byte[] data) {
        Reader r = new Reader(data);
        PageEvent p = new PageEvent(r.readString(MAX_EVENT_NAME_LENGTH), r.readString(MAX_EVENT_DATA_LENGTH));
        return r.finish(p);
    }

    public static AssetRequest decodeAssetRequest(byte[] data) {
        Reader r = new Reader(data);
        return r.finish(new AssetRequest(r.readString(MAX_ASSET_PATH_LENGTH)));
    }

    // --- primitives ---

    /** Appends the three wire primitives to a growing buffer. */
    public static final class Writer {
        private byte[] buf = new byte[64];
        private int size;

        private void ensure(int extra) {
            int needed = size + extra;
            if (needed > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(needed, buf.length * 2));
            }
        }

        public Writer writeVarInt(int value) {
            ensure(5);
            // Unsigned shift, as in the game: a negative number runs the full five bytes
            // instead of looping forever.
            while ((value & -128) != 0) {
                buf[size++] = (byte) ((value & 127) | 128);
                value >>>= 7;
            }
            buf[size++] = (byte) value;
            return this;
        }

        public Writer writeString(String value, int maxLength) {
            if (value == null) {
                throw new WireException("String is null");
            }
            // Characters, not bytes: the same check the game makes before it encodes.
            if (value.length() > maxLength) {
                throw new WireException("String too big (was " + value.length() + " characters, max " + maxLength + ")");
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxLength * 3) {
                throw new WireException("String too big (was " + bytes.length + " bytes encoded, max " + maxLength * 3 + ")");
            }
            writeVarInt(bytes.length);
            return writeRaw(bytes);
        }

        public Writer writeByteArray(byte[] value, int maxLength) {
            if (value == null) {
                throw new WireException("Byte array is null");
            }
            if (value.length > maxLength) {
                throw new WireException("ByteArray with size " + value.length + " is bigger than allowed " + maxLength);
            }
            writeVarInt(value.length);
            return writeRaw(value);
        }

        private Writer writeRaw(byte[] bytes) {
            ensure(bytes.length);
            System.arraycopy(bytes, 0, buf, size, bytes.length);
            size += bytes.length;
            return this;
        }

        public byte[] toByteArray() {
            return Arrays.copyOf(buf, size);
        }
    }

    /** Reads the three wire primitives from a byte array, checking every length first. */
    public static final class Reader {
        private final byte[] data;
        private int pos;

        public Reader(byte[] data) {
            if (data == null) {
                throw new WireException("Payload is null");
            }
            this.data = data;
        }

        public int remaining() {
            return data.length - pos;
        }

        private byte readByte() {
            if (pos >= data.length) {
                throw new WireException("Payload ended early");
            }
            return data[pos++];
        }

        public int readVarInt() {
            int result = 0;
            for (int i = 0; i < 5; i++) {
                byte b = readByte();
                result |= (b & 127) << (i * 7);
                if ((b & 128) != 128) {
                    return result;
                }
            }
            throw new WireException("VarInt too big");
        }

        public String readString(int maxLength) {
            int maxEncoded = maxLength * 3;
            int length = readVarInt();
            if (length > maxEncoded) {
                throw new WireException("The received encoded string buffer length is longer than maximum allowed ("
                        + length + " > " + maxEncoded + ")");
            }
            if (length < 0) {
                throw new WireException("The received encoded string buffer length is less than zero! Weird string!");
            }
            requireBytes(length);
            // Malformed UTF-8 decodes to U+FFFD rather than failing, as it does in the game.
            String s = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            if (s.length() > maxLength) {
                throw new WireException("The received string length is longer than maximum allowed ("
                        + s.length() + " > " + maxLength + ")");
            }
            return s;
        }

        public byte[] readByteArray(int maxLength) {
            int length = readVarInt();
            if (length < 0) {
                throw new WireException("ByteArray with negative size " + length);
            }
            if (length > maxLength) {
                throw new WireException("ByteArray with size " + length + " is bigger than allowed " + maxLength);
            }
            requireBytes(length);
            byte[] out = Arrays.copyOfRange(data, pos, pos + length);
            pos += length;
            return out;
        }

        private void requireBytes(int length) {
            if (length > remaining()) {
                throw new WireException("Payload ended early (wanted " + length + " bytes, " + remaining() + " left)");
            }
        }

        /** Hands back the decoded payload, provided nothing is left over. */
        <T> T finish(T payload) {
            if (remaining() != 0) {
                throw new WireException(remaining() + " unexpected byte(s) after the last field");
            }
            return payload;
        }
    }
}
