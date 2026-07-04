package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildExecutorIncrementalTest {

    @TempDir
    Path project;

    @Test
    void secondBuildSkipsCompilationAndLinkUntilSourceChanges() throws Exception {
        Path sourceDir = Files.createDirectories(project.resolve("src"));
        Path source = Files.writeString(sourceDir.resolve("main.c"),
                "int main(void) { return 0; }");
        Path compiler = Files.writeString(project.resolve("fake-cc"), "compiler");
        Path buildDir = project.resolve("build");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setName("app");
        manifest.setSourceFolders(List.of("src"));
        AtomicInteger executions = new AtomicInteger();
        ProcessExecutor executor = (command, workingDir, env, output) -> {
            executions.incrementAndGet();
            try {
                if (command.contains("-c")) {
                    Path object = Path.of(command.get(command.indexOf("-o") + 1));
                    Files.createDirectories(object.getParent());
                    Files.writeString(object, "object:" + Files.readString(source));
                    Path depFile = Path.of(command.get(command.indexOf("-MF") + 1));
                    Files.writeString(depFile, object + ": " + source + System.lineSeparator());
                } else {
                    Path artifact = Path.of(command.get(command.indexOf("-o") + 1));
                    Files.writeString(artifact, "binary-" + executions.get());
                }
                return 0;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        BuildRequest request = new BuildRequest(project, manifest,
                new Toolchain(ToolchainKind.CUSTOM, compiler, compiler), BuildSystem.DEFAULT,
                project.resolve("packages"), buildDir, "Debug", ignored -> { }, 1,
                List.of(), true, executor);

        assertTrue(BuildExecutor.build(request).success());
        assertEquals(2, executions.get());
        assertTrue(BuildExecutor.build(request).success());
        assertEquals(2, executions.get());

        Files.writeString(source, "int main(void) { return 1; }");
        assertTrue(BuildExecutor.build(request).success());
        assertEquals(4, executions.get());
    }
}
