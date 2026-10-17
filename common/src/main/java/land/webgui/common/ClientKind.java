package land.webgui.common;

/** What the far end of a connection turned out to be. */
public enum ClientKind {
    /** Speaks this protocol: the handshake channel is registered. */
    CURRENT,
    /** Has WebGUI, but an older build without the handshake channel. */
    OUTDATED,
    /** No WebGUI at all — a vanilla client, or one that simply does not have it. */
    ABSENT,
}
