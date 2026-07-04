package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public record BuildRequest(
        Path projectPath,
        ManifestRootModel manifest,
        Toolchain toolchain,
        BuildSystem buildSystem,
        Path packagesDir,
        Path buildDir,
        String buildMode,
        Consumer<String> output,
        int jobs,
        List<String> onlyTargets,
        boolean incremental,
        ProcessExecutor executor) {

    public BuildRequest {
        onlyTargets = onlyTargets == null ? List.of() : onlyTargets;
        executor = executor == null ? ProcessExecutor.REAL : executor;
    }

    public BuildRequest(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                        BuildSystem buildSystem, Path packagesDir, Path buildDir,
                        String buildMode, Consumer<String> output) {
        this(projectPath, manifest, toolchain, buildSystem, packagesDir, buildDir, buildMode,
                output, 0, List.of(), true, null);
    }

    public BuildRequest(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                        BuildSystem buildSystem, Path packagesDir, Path buildDir,
                        String buildMode, Consumer<String> output, int jobs,
                        List<String> onlyTargets) {
        this(projectPath, manifest, toolchain, buildSystem, packagesDir, buildDir, buildMode,
                output, jobs, onlyTargets, true, null);
    }
}
