package dtm.builder.build.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PreprocessedObjectCacheTest {
    @TempDir Path temp;
    @Test void lineMarkersInsideMacroExpandedAsmAreNotMistakenForExternalDirectives() throws Exception {
        Path input = Files.writeString(temp.resolve("input.ii"), """
                asm volatile("cpuid\\n\\t" : "=a" (
                # 24 "/checkout/src/cpu_info.cpp" 3
                a
                # 24 "/checkout/src/cpu_info.cpp"
                ));
                """);
        assertNotNull(PreprocessedObjectCache.preprocessedHash(input, null));
    }
    @Test void encodedOrSplitExternalAsmDirectivesCannotBecomeCacheHits() throws Exception {
        for (String code : new String[]{"asm(\".in\" \"cbin file\");", "asm(\"\\x2eincbin file\");", "asm(\".include file\");"}) {
            Path input = Files.writeString(temp.resolve("input.ii"), code);
            assertNull(PreprocessedObjectCache.preprocessedHash(input, null), code);
        }
    }
}
