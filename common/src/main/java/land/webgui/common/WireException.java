package land.webgui.common;

/** A payload that does not follow the wire format, or a value that cannot be written in it. */
public final class WireException extends RuntimeException {
    public WireException(String message) {
        super(message);
    }
}
