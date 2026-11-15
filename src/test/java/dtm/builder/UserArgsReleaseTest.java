package dtm.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserArgsReleaseTest {
    @Test void releaseBuildsAndTestsWithoutEitherCache() {
        for (String[] command : new String[][] {
                {"--release"}, {".", "build", "--release"}, {".", "install", "--release"},
                {".", "clean", "--release"}, {".", "refresh", "--release"}}) {
            var args = new UserArgs(command);
            assertFalse(args.isInvalidCommand(), args.getInvalidReason());
            assertTrue(args.hasRelease());
            assertTrue(args.hasBuild());
            assertTrue(args.hasTest());
            assertTrue(args.hasNoIncremental());
            assertFalse(args.getBuildOptions().sharedCache());
        }
    }

    @Test void releaseRejectsOptionsThatWouldBypassTheBuildOrTests() {
        for (String option : new String[] {"--interactive", "--compile-commands", "--clangd", "--skip-tests"}) {
            assertTrue(new UserArgs(new String[] {"--release", option}).isInvalidCommand(), option);
            assertTrue(new UserArgs(new String[] {option, "--release"}).isInvalidCommand(), option);
        }
        assertTrue(new UserArgs(new String[] {"--reliese"}).isInvalidCommand());
    }

    @Test void regularBuildsKeepTheirDefaults() {
        var args = new UserArgs(new String[] {"build"});
        assertFalse(args.hasRelease());
        assertFalse(args.hasTest());
        assertFalse(args.hasNoIncremental());
        assertTrue(args.getBuildOptions().sharedCache());
    }
}
