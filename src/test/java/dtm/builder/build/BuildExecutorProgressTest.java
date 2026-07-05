package dtm.builder.build;

import dtm.builder.build.graph.TargetGraph;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildExecutorProgressTest {

    @TempDir
    Path project;

    @Test
    void countsEachSourceAndFinalLinkButNotHeaders() throws Exception {
        Path src = Files.createDirectories(project.resolve("src"));
        Files.writeString(src.resolve("main.cpp"), "int main() { return 0; }");
        Files.writeString(src.resolve("app.cpp"), "int app() { return 1; }");
        Files.writeString(src.resolve("app.h"), "int app();");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setSources(List.of("src"));
        TargetGraph graph = TargetGraph.of(
                TargetResolver.resolve(manifest, project, false).targets());

        assertEquals(3, BuildExecutor.actionCount(project, graph));
    }
}
