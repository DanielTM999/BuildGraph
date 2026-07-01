package dtm.bulder;

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
    void unknownArgumentIsInvalid() {
        UserArgs a = new UserArgs(new String[]{"/p", "--bogus"});
        assertTrue(a.isInvalidCommand());
    }
}
