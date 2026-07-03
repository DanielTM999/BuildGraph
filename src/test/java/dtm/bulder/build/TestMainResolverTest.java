package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMainResolverTest {

    @TempDir
    Path projectPath;

    @Test
    void findsSingleMainWhenTestMainIsBlank() throws IOException {
        Path helper = source("tests/MathTests.cpp", "void testMath() {}\n");
        Path main = source("tests/TestMain.cpp", "int main() { return 0; }\n");

        TestMainResolver.Result result = TestMainResolver.resolve(projectPath,
                new ManifestRootModel(), List.of(helper, main));

        assertTrue(result.success());
        assertEquals(main, result.source());
    }

    @Test
    void usesTestMainRelativeToTestFolder() throws IOException {
        Path main = source("specs/Runner.cpp", "int main() { return 0; }\n");
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTestFolder("specs");
        manifest.setTestMain("Runner.cpp");

        TestMainResolver.Result result = TestMainResolver.resolve(
                projectPath, manifest, List.of(main));

        assertTrue(result.success());
        assertEquals(main, result.source());
    }

    @Test
    void rejectsMissingOrAmbiguousMain() throws IOException {
        Path helper = source("tests/Helper.cpp", "void helper() {}\n");
        TestMainResolver.Result missing = TestMainResolver.resolve(projectPath,
                new ManifestRootModel(), List.of(helper));
        assertFalse(missing.success());
        assertTrue(missing.error().contains("Nenhum main()"));

        Path first = source("tests/First.cpp", "int main() { return 0; }\n");
        Path second = source("tests/Second.cpp", "auto main() -> int { return 0; }\n");
        TestMainResolver.Result ambiguous = TestMainResolver.resolve(projectPath,
                new ManifestRootModel(), List.of(first, second));
        assertFalse(ambiguous.success());
        assertTrue(ambiguous.error().contains("Mais de um main()"));
    }

    private Path source(String relative, String contents) throws IOException {
        Path path = projectPath.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, contents);
    }
}
