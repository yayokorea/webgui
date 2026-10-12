package land.webgui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareTest {

    private static void assertOrdered(String older, String newer) {
        assertTrue(VersionCompare.compare(older, newer) < 0, older + " < " + newer);
        assertTrue(VersionCompare.compare(newer, older) > 0, newer + " > " + older);
        assertTrue(VersionCompare.isNewer(newer, older));
        assertFalse(VersionCompare.isNewer(older, newer));
    }

    private static void assertSame(String a, String b) {
        assertEquals(0, VersionCompare.compare(a, b), a + " == " + b);
        assertEquals(0, VersionCompare.compare(b, a), b + " == " + a);
        assertFalse(VersionCompare.isNewer(a, b));
        assertFalse(VersionCompare.isNewer(b, a));
    }

    @Test
    void ordersByNumberNotByText() {
        assertOrdered("1.9.0", "1.10.0");
        assertOrdered("1.8.1", "1.8.2");
        assertOrdered("1.8.9", "1.9.0");
        assertOrdered("1.99.99", "2.0.0");
    }

    @Test
    void missingComponentsCountAsZero() {
        assertSame("1.8", "1.8.0");
        assertSame("2", "2.0.0");
        assertOrdered("1.8", "1.8.1");
    }

    @Test
    void ignoresBuildMetadata() {
        // Our own builds are named 1.8.1+mc1.21.11; the release list says 1.8.1.
        assertSame("1.8.1+mc1.21.11", "1.8.1");
        assertSame("1.8.1+mc1.21.11", "1.8.1+mc1.20.1");
        assertOrdered("1.8.1+mc1.21.11", "1.8.2");
        assertOrdered("1.8.1+mc1.21.11", "1.9.0+mc1.20.1");
    }

    @Test
    void ignoresALeadingV() {
        assertSame("v1.8.1", "1.8.1");
        assertSame("V1.8.1", "1.8.1");
        assertOrdered("v1.8.1", "v1.8.2");
    }

    @Test
    void aPreReleaseComesBeforeItsRelease() {
        assertOrdered("1.8.1-beta.1", "1.8.1");
        assertOrdered("1.8.1-beta.1+mc1.21.11", "1.8.1+mc1.21.11");
        assertOrdered("1.8.0", "1.8.1-beta.1");
    }

    @Test
    void preReleasesFollowSemverPrecedence() {
        assertOrdered("1.0.0-alpha", "1.0.0-alpha.1");
        assertOrdered("1.0.0-alpha.1", "1.0.0-alpha.beta");
        assertOrdered("1.0.0-alpha.beta", "1.0.0-beta");
        assertOrdered("1.0.0-beta", "1.0.0-beta.2");
        assertOrdered("1.0.0-beta.2", "1.0.0-beta.11");
        assertOrdered("1.0.0-beta.11", "1.0.0-rc.1");
        assertOrdered("1.0.0-rc.1", "1.0.0");
        assertSame("1.0.0-beta.1", "1.0.0-beta.1+build.7");
    }

    @Test
    void hugeNumericPreReleaseIdentifiersDoNotOverflow() {
        assertOrdered("1.0.0-99999999999999999999", "1.0.0-100000000000000000000");
        assertSame("1.0.0-007", "1.0.0-7");
    }

    @Test
    void rejectsAnythingThatIsNotAVersion() {
        for (String bad : new String[] {"", "   ", "latest", "1.x.0", "1..2", "1.2.", ".1", "v", "-beta", "1.0.0-", "1.-2", "abc.def"}) {
            assertThrows(IllegalArgumentException.class, () -> VersionCompare.compare(bad, "1.0.0"), bad);
            assertThrows(IllegalArgumentException.class, () -> VersionCompare.compare("1.0.0", bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> VersionCompare.compare(null, "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionCompare.compare("99999999999999999999.0", "1.0.0"));
    }
}
