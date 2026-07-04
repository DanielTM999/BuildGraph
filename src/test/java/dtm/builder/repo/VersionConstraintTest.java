package dtm.builder.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionConstraintTest {

    @Test
    void supportsExactAndSimpleRanges() {
        assertTrue(VersionConstraint.exact("1.2").matches("1.2.0"));
        assertFalse(VersionConstraint.exact("1.2").matches("1.2.1"));

        assertTrue(VersionConstraint.parse("[1.0,2.0)").matches("1.9.5"));
        assertFalse(VersionConstraint.parse("[1.0,2.0)").matches("2.0"));
        assertTrue(VersionConstraint.parse(">=1.2 <2.0").matches("1.5"));
        assertTrue(VersionConstraint.parse("^1.2.3").matches("1.9.0"));
        assertFalse(VersionConstraint.parse("^1.2.3").matches("2.0.0"));
        assertTrue(VersionConstraint.parse("1.4.x").matches("1.4.99"));
        assertFalse(VersionConstraint.parse("1.4.x").matches("1.5.0"));
    }

    @Test
    void ordersPrereleasesBeforeStableVersions() {
        assertTrue(PackageVersion.parse("1.0.0")
                .compareTo(PackageVersion.parse("1.0.0-rc.1")) > 0);
    }
}
