package dtm.bulder.lifecycle;

import dtm.bulder.build.BuildSystem;
import dtm.bulder.build.Toolchain;
import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.placeholder.PlaceholderResolver;
import dtm.bulder.repo.GlobalRepository;

import java.nio.file.Path;
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
        Consumer<String> info) {
}
