package dtm.builder.lifecycle;

import dtm.builder.build.BuildSystem;
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
        boolean incremental) {

    public LifecycleContext {
        onlyTargets = onlyTargets == null ? List.of() : onlyTargets;
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
