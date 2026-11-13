package dtm.builder.build;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BuildEnvironmentTest {
    @Test void unrelatedInvocationMetadataDoesNotInvalidateObjects() {
        assertEquals(BuildEnvironment.signature(Map.of("PATH", "toolchain", "INVOCATION_ID", "one"), Map.of()),
                BuildEnvironment.signature(Map.of("PATH", "toolchain", "INVOCATION_ID", "two"), Map.of()));
    }
    @Test void searchPathsSdkEpochCompilerOptionsAndExplicitOverridesAreRelevant() {
        for (String name : new String[]{"PATH", "CPATH", "INCLUDE", "CL", "_CL_", "SOURCE_DATE_EPOCH", "SDKROOT", "LC_ALL", "GCC_EXEC_PREFIX"})
            assertNotEquals(BuildEnvironment.signature(Map.of(name, "one"), Map.of()), BuildEnvironment.signature(Map.of(name, "two"), Map.of()), name);
        assertNotEquals(BuildEnvironment.signature(Map.of(), Map.of("CUSTOM", "one")), BuildEnvironment.signature(Map.of(), Map.of("CUSTOM", "two")));
    }
    @Test void shellOnlyVariablesDoNotInvalidateObjects() {
        for (String name : new String[]{"TMP", "TEMP", "TMPDIR", "LANGUAGE", "LC_MESSAGES"})
            assertEquals(BuildEnvironment.signature(Map.of(name, "one"), Map.of()), BuildEnvironment.signature(Map.of(name, "two"), Map.of()), name);
    }
    @Test void resolvedCompilerToolsIgnoreSearchPathButKeepCompilerInputs() {
        assertEquals(BuildEnvironment.resolvedToolSignature(Map.of("PATH", "powershell", "CPATH", "inc"), Map.of(), false),
                BuildEnvironment.resolvedToolSignature(Map.of("PATH", "bash", "CPATH", "inc"), Map.of(), false));
        assertEquals(BuildEnvironment.resolvedToolSignature(Map.of("LANG", "pt_BR.UTF-8"), Map.of(), true),
                BuildEnvironment.resolvedToolSignature(Map.of(), Map.of(), true));
        assertNotEquals(BuildEnvironment.resolvedToolSignature(Map.of("LANG", "C"), Map.of(), false),
                BuildEnvironment.resolvedToolSignature(Map.of("LANG", "C.UTF-8"), Map.of(), false));
        for (String name : new String[]{"CPATH", "INCLUDE", "SOURCE_DATE_EPOCH", "GCC_EXEC_PREFIX"})
            assertNotEquals(BuildEnvironment.resolvedToolSignature(Map.of(name, "one"), Map.of(), true),
                    BuildEnvironment.resolvedToolSignature(Map.of(name, "two"), Map.of(), true), name);
    }
    @Test void customToolWrappersConservativelyIncludeEveryInheritedVariable() {
        assertNotEquals(BuildEnvironment.customSignature(Map.of("CUSTOM_WRAPPER_MODE", "one"), Map.of()),
                BuildEnvironment.customSignature(Map.of("CUSTOM_WRAPPER_MODE", "two"), Map.of()));
    }
}
