package dtm.builder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserArgsTargetsTest {

    @Test
    void parsesSingleTarget() {
        UserArgs args = new UserArgs(new String[]{"build", "--target", "vema"});
        assertEquals(List.of("vema"), args.getTargets());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void parsesRepeatedAndCommaSeparatedTargets() {
        UserArgs args = new UserArgs(new String[]{
                "build", "--target", "vema,vemac", "-t", "core"});
        assertEquals(List.of("vema", "vemac", "core"), args.getTargets());
    }

    @Test
    void targetWithoutValueIsInvalid() {
        UserArgs args = new UserArgs(new String[]{"build", "--target"});
        assertTrue(args.isInvalidCommand());
    }

    @Test
    void parsesJobs() {
        UserArgs args = new UserArgs(new String[]{"build", "-j", "4"});
        assertEquals(4, args.getJobs());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void jobsDefaultsToZeroWhenAbsent() {
        UserArgs args = new UserArgs(new String[]{"build"});
        assertEquals(0, args.getJobs());
    }

    @Test
    void nonNumericJobsIsInvalid() {
        UserArgs args = new UserArgs(new String[]{"build", "--jobs", "muitos"});
        assertTrue(args.isInvalidCommand());
    }
}
