package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceCollectorTest {

    @TempDir
    Path projectPath;

    @Test
    void defaultsToTestsFolderWhenManifestValueIsBlank() throws IOException {
        Path expected = createSource("tests/DefaultTest.cpp");
        createSource("test/IgnoredTest.cpp");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTestFolder("   ");

        assertEquals(List.of(expected), SourceCollector.collectTestSources(projectPath, manifest));
    }

    @Test
    void usesTestFolderDeclaredInManifest() throws IOException {
        Path expected = createSource("specs/CustomTest.cpp");
        createSource("tests/IgnoredTest.cpp");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTestFolder("specs");

        assertEquals(List.of(expected), SourceCollector.collectTestSources(projectPath, manifest));
    }

    private Path createSource(String relativePath) throws IOException {
        Path source = projectPath.resolve(relativePath);
        Files.createDirectories(source.getParent());
        return Files.createFile(source);
    }
}
