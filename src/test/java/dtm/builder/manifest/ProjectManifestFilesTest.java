package dtm.builder.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectManifestFilesTest {

    @TempDir
    Path project;

    @Test
    void defaultBuildDirIsDirectlyUnderProject() {
        assertEquals(project.resolve("build").toAbsolutePath().normalize(),
                ProjectManifestFiles.defaultBuildDir(project));
    }

    @Test
    void nullEmptyAndBlankOutputUseDefaultBuildDir() {
        Path expected = project.resolve("build").toAbsolutePath().normalize();

        assertEquals(expected, ProjectManifestFiles.resolveBuildDir(project, null));
        assertEquals(expected, ProjectManifestFiles.resolveBuildDir(project, ""));
        assertEquals(expected, ProjectManifestFiles.resolveBuildDir(project, "   "));
    }

    @Test
    void resolvesRelativeAndAbsoluteOutputDir() {
        Path absolute = project.resolveSibling("absolute-build").toAbsolutePath().normalize();

        assertEquals(project.resolve("out/debug").toAbsolutePath().normalize(),
                ProjectManifestFiles.resolveBuildDir(project, "out/debug"));
        assertEquals(absolute, ProjectManifestFiles.resolveBuildDir(project, absolute.toString()));
    }

    @Test
    void manifestPackagesBaseWinsAndCliIsFallback() {
        assertEquals(project.resolve("manifest-packages").toAbsolutePath().normalize(),
                ProjectManifestFiles.resolvePackagesDir(project, "manifest-packages",
                        "cli-packages"));
        assertEquals(project.resolve("cli-packages").toAbsolutePath().normalize(),
                ProjectManifestFiles.resolvePackagesDir(project, null, "cli-packages"));
        assertEquals(project.resolve(".buildgraph/packages").toAbsolutePath().normalize(),
                ProjectManifestFiles.resolvePackagesDir(project, " ", " "));
    }
}
