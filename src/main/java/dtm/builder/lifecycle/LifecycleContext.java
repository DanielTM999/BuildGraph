package dtm.builder.lifecycle;

import dtm.builder.build.BuildOptions;
import dtm.builder.build.BuildSystem;
import dtm.builder.build.ProcessExecutor;
import dtm.builder.build.Toolchain;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.placeholder.PlaceholderResolver;
import dtm.builder.repo.GlobalRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public record LifecycleContext(
        Path projectPath,
        ManifestRootModel manifest,
        Toolchain toolchain,
        BuildSystem buildSystem,
        GlobalRepository repo,
        Path packagesDir,
        Path buildDir,
        String buildMode,
        PlaceholderResolver placeholders,
        Consumer<String> output,
        Consumer<String> info,
        int jobs,
        List<String> onlyTargets,
        boolean incremental,
        ProcessExecutor executor,
        BuildOptions options) {

    public LifecycleContext {
        onlyTargets = onlyTargets == null ? List.of() : onlyTargets;
        executor = executor == null ? ProcessExecutor.REAL : executor;
        options = options == null ? BuildOptions.defaults() : options;
    }

    public LifecycleContext(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                            BuildSystem buildSystem, GlobalRepository repo, Path packagesDir,
                            Path buildDir, String buildMode, PlaceholderResolver placeholders,
                            Consumer<String> output, Consumer<String> info, int jobs,
                            List<String> onlyTargets, boolean incremental, ProcessExecutor executor) {
        this(projectPath, manifest, toolchain, buildSystem, repo, packagesDir, buildDir, buildMode,
                placeholders, output, info, jobs, onlyTargets, incremental, executor, null);
    }

    public LifecycleContext(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                            BuildSystem buildSystem, GlobalRepository repo, Path packagesDir,
                            Path buildDir, String buildMode, PlaceholderResolver placeholders,
                            Consumer<String> output, Consumer<String> info, int jobs,
                            List<String> onlyTargets, boolean incremental) {
        this(projectPath, manifest, toolchain, buildSystem, repo, packagesDir, buildDir, buildMode,
                placeholders, output, info, jobs, onlyTargets, incremental, null);
    }

    public LifecycleContext(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                            BuildSystem buildSystem, GlobalRepository repo, Path packagesDir,
                            Path buildDir, String buildMode, PlaceholderResolver placeholders,
                            Consumer<String> output, Consumer<String> info) {
        this(projectPath, manifest, toolchain, buildSystem, repo, packagesDir, buildDir,
                buildMode, placeholders, output, info, 0, List.of(), true);
    }

    public LifecycleContext(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                            BuildSystem buildSystem, GlobalRepository repo, Path packagesDir,
                            Path buildDir, String buildMode, PlaceholderResolver placeholders,
                            Consumer<String> output, Consumer<String> info, int jobs,
                            List<String> onlyTargets) {
        this(projectPath, manifest, toolchain, buildSystem, repo, packagesDir, buildDir,
                buildMode, placeholders, output, info, jobs, onlyTargets, true);
    }
}
