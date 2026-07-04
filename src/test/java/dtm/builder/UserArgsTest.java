package dtm.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserArgsTest {

    @Test
    void noArgsInfersBuildInCurrentDir() {
        UserArgs a = new UserArgs(new String[]{});
        assertTrue(a.hasBuild());
        assertFalse(a.hasProjectPath());
        assertEquals(System.getProperty("user.dir"), a.getProjectPath());
        assertFalse(a.isInvalidCommand());
    }

    @Test
    void firstKeywordIsNotAProjectPath() {
        UserArgs a = new UserArgs(new String[]{"build"});
        assertFalse(a.hasProjectPath());
        assertTrue(a.hasBuild());
    }

    @Test
    void firstNonKeywordIsProjectPathAndBuildInferred() {
        UserArgs a = new UserArgs(new String[]{"/some/path"});
        assertTrue(a.hasProjectPath());
        assertEquals("/some/path", a.getProjectPath());
        assertTrue(a.hasBuild());
    }

    @Test
    void explicitPhasesDoNotInferBuild() {
        UserArgs a = new UserArgs(new String[]{"/p", "clean", "test"});
        assertTrue(a.hasClean());
        assertTrue(a.hasTest());
        assertFalse(a.hasBuild());
    }

    @Test
    void parsesFlagsWithValues() {
        UserArgs a = new UserArgs(new String[]{
                "/p", "-f", "json", "-p", "dev", "-c", "clang++", "--repo", "/r"});
        assertEquals("json", a.getFormat());
        assertEquals("dev", a.getProfile());
        assertEquals("clang++", a.getCompiler());
        assertEquals("/r", a.getRepoPath());
        assertTrue(a.hasBuild(), "no phase given, build inferred");
    }

    @Test
    void recognizesInteractiveRefreshHelp() {
        assertTrue(new UserArgs(new String[]{"--interactive"}).isInteractive());
        assertFalse(new UserArgs(new String[]{"--interactive"}).hasBuild());
        assertTrue(new UserArgs(new String[]{"refresh"}).hasRefresh());
        assertFalse(new UserArgs(new String[]{"refresh"}).hasBuild());
        assertTrue(new UserArgs(new String[]{"-h"}).hasHelp());
    }

    @Test
    void compileCommandsFlagDoesNotInferBuild() {
        UserArgs clangd = new UserArgs(new String[]{"--clangd"});
        UserArgs canonical = new UserArgs(new String[]{"--compile-commands"});

        assertTrue(clangd.hasCompileCommands());
        assertTrue(canonical.hasCompileCommands());
        assertFalse(clangd.hasBuild());
        assertFalse(canonical.hasBuild());
    }

    @Test
    void parsesNoIncrementalFlag() {
        UserArgs args = new UserArgs(new String[]{"build", "--no-incremental"});

        assertTrue(args.hasBuild());
        assertTrue(args.hasNoIncremental());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void lockCommandDoesNotInferBuild() {
        UserArgs args = new UserArgs(new String[]{"lock"});

        assertTrue(args.hasLock());
        assertFalse(args.hasBuild());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void unknownArgumentIsInvalid() {
        UserArgs a = new UserArgs(new String[]{"/p", "--bogus"});
        assertTrue(a.isInvalidCommand());
    }

    @Test
    void parsesTestMainAndRejectsMissingValue() {
        UserArgs configured = new UserArgs(new String[]{"test", "--test-main", "TestMain.cpp"});
        assertEquals("TestMain.cpp", configured.getTestMain());
        assertFalse(configured.isInvalidCommand());

        UserArgs missing = new UserArgs(new String[]{"test", "--test-main"});
        assertTrue(missing.isInvalidCommand());
    }
}
