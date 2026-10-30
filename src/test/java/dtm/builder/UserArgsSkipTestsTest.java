package dtm.builder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserArgsSkipTestsTest {

    @Test
    void parsesSkipAllTests() {
        UserArgs args = new UserArgs(new String[]{"test", "--skip-tests"});
        assertTrue(args.hasSkipTests());
        assertTrue(args.hasTest());
        assertEquals(List.of(), args.getSkippedTests());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void parsesRepeatedAndCommaSeparatedSkippedTests() {
        UserArgs args = new UserArgs(new String[]{
                "test", "--skip-test", "jit/alloc_test.cpp,gc", "--skip-test", "**/*_bench.cpp"});
        assertFalse(args.hasSkipTests());
        assertEquals(List.of("jit/alloc_test.cpp", "gc", "**/*_bench.cpp"), args.getSkippedTests());
        assertFalse(args.isInvalidCommand());
    }

    @Test
    void skipTestWithoutValueIsInvalid() {
        UserArgs missing = new UserArgs(new String[]{"test", "--skip-test"});
        assertTrue(missing.isInvalidCommand());
        assertEquals("--skip-test requer um arquivo de teste", missing.getInvalidReason());
        assertTrue(new UserArgs(new String[]{"test", "--skip-test", ","}).isInvalidCommand());
        assertEquals("--target requer o id de um target",
                new UserArgs(new String[]{"--target"}).getInvalidReason());
    }
}
