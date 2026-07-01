package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.function.Consumer;

public record BuildRequest(
        Path projectPath,
        ManifestRootModel manifest,
        Toolchain toolchain,
        BuildSystem buildSystem,
        Path packagesDir,
        Path buildDir,
        String buildMode,
        Consumer<String> output) {
}
