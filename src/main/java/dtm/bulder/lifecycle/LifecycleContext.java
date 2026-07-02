package dtm.bulder.lifecycle;

import dtm.bulder.build.BuildSystem;
import dtm.bulder.build.Toolchain;
import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.placeholder.PlaceholderResolver;
import dtm.bulder.repo.GlobalRepository;

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
        List<String> onlyTargets) {

    public LifecycleContext {
        onlyTargets = onlyTargets == null ? List.of() : onlyTargets;
    }

    public LifecycleContext(Path projectPath, ManifestRootModel manifest, Toolchain toolchain,
                            BuildSystem buildSystem, GlobalRepository repo, Path packagesDir,
                            Path buildDir, String buildMode, PlaceholderResolver placeholders,
                            Consumer<String> output, Consumer<String> info) {
        this(projectPath, manifest, toolchain, buildSystem, repo, packagesDir, buildDir,
                buildMode, placeholders, output, info, 0, List.of());
    }
}
