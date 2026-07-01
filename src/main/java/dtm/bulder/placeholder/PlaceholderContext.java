package dtm.bulder.placeholder;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaceholderContext {

    private final Path projectDir;
    private final ManifestRootModel manifest;
    private final Map<String, String> env;

    public PlaceholderContext(Path projectDir, ManifestRootModel manifest) {
        this.projectDir = projectDir == null ? null : projectDir.toAbsolutePath().normalize();
        this.manifest = manifest;
        this.env = buildEnv(manifest);
    }

    private static Map<String, String> buildEnv(ManifestRootModel manifest) {
        Map<String, String> out = new LinkedHashMap<>(System.getenv());
        if (manifest != null && manifest.getEnv() != null) {
            out.putAll(manifest.getEnv());
        }
        return out;
    }

    public Path projectDir() {
        return projectDir;
    }

    public ManifestRootModel manifest() {
        return manifest;
    }

    public Map<String, String> env() {
        return env;
    }
}
