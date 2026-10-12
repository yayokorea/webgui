package land.webgui.common;

/**
 * Orders version strings the way a release list reads: {@code 1.10.0} after {@code 1.9.0},
 * {@code 1.8.1-beta.1} before {@code 1.8.1}.
 *
 * Self-contained so the update check behaves the same on every platform instead of
 * borrowing whichever loader's version class happens to be around. A leading {@code v}
 * and {@code +build} metadata (the {@code +mc1.21.11} our own builds carry) are ignored.
 */
public final class VersionCompare {
    private VersionCompare() {}

    /** Negative, zero or positive as {@code a} is older than, equal to or newer than {@code b}. */
    public static int compare(String a, String b) {
        Parsed pa = parse(a);
        Parsed pb = parse(b);
        int n = Math.max(pa.core.length, pb.core.length);
        for (int i = 0; i < n; i++) {
            // A missing component counts as zero, so 1.8 equals 1.8.0.
            long x = i < pa.core.length ? pa.core[i] : 0;
            long y = i < pb.core.length ? pb.core[i] : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return comparePreRelease(pa.pre, pb.pre);
    }

    public static boolean isNewer(String candidate, String than) {
        return compare(candidate, than) > 0;
    }

    private record Parsed(long[] core, String[] pre) {}

    private static Parsed parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("version is null");
        }
        String v = raw.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int plus = v.indexOf('+');
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
        String[] pre = new String[0];
        int dash = v.indexOf('-');
        if (dash >= 0) {
            String tail = v.substring(dash + 1);
            if (tail.isEmpty()) {
                throw new IllegalArgumentException("empty pre-release in '" + raw + "'");
            }
            pre = tail.split("\\.", -1);
            v = v.substring(0, dash);
        }
        if (v.isEmpty()) {
            throw new IllegalArgumentException("no version number in '" + raw + "'");
        }
        String[] parts = v.split("\\.", -1);
        long[] core = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            core[i] = number(parts[i], raw);
        }
        return new Parsed(core, pre);
    }

    private static long number(String part, String raw) {
        if (part.isEmpty() || !isDigits(part)) {
            throw new IllegalArgumentException("not a version number: '" + raw + "'");
        }
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version component too large in '" + raw + "'");
        }
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** SemVer 2.0: no pre-release beats any pre-release; otherwise identifier by identifier. */
    private static int comparePreRelease(String[] a, String[] b) {
        if (a.length == 0 || b.length == 0) {
            return Integer.compare(a.length == 0 ? 1 : 0, b.length == 0 ? 1 : 0);
        }
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int c = compareIdentifier(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        // All shared identifiers equal: the longer list is the later one.
        return Integer.compare(a.length, b.length);
    }

    private static int compareIdentifier(String a, String b) {
        boolean na = !a.isEmpty() && isDigits(a);
        boolean nb = !b.isEmpty() && isDigits(b);
        if (na && nb) {
            // Strip leading zeros and compare by length first, so a long run of digits
            // cannot overflow a long.
            String x = a.replaceFirst("^0+(?=.)", "");
            String y = b.replaceFirst("^0+(?=.)", "");
            return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
        }
        if (na) {
            return -1; // numeric identifiers rank below alphanumeric ones
        }
        if (nb) {
            return 1;
        }
        return a.compareTo(b);
    }
}
