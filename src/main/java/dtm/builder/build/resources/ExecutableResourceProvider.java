package dtm.builder.build.resources;

import dtm.builder.build.BuildRequest;
import dtm.builder.build.TargetPlatform;
import dtm.builder.build.Toolchain;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

interface ExecutableResourceProvider {
    record Output(List<Path> files, List<Path> inputs, List<String> linkFlags) { }
    Path tool(TargetPlatform platform, Toolchain compiler, boolean msvcLink, Path project) throws IOException;
    Output output(Path dir, boolean icon, boolean msvcLink, boolean driverLink);
    void generate(BuildRequest req, ManifestRootModel manifest, TargetPlatform platform,
                  Toolchain compiler, Path tool, Path icon, Map<String, String> metadata,
                  Path dir, Output output, Consumer<String> log) throws IOException;
}
