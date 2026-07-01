package dtm.bulder.manifest.model;

import java.nio.file.Path;

public record ResolvedDependency(
        ManifestPackagesModel declared,
        LibraryManifest libraryManifest,
        Path globalLibraryDir,
        Path globalManifestFile,
        String platformToken) {

    public String id() {
        return declared == null ? "" : declared.getId();
    }

    public String key() {
        return declared == null ? "" : declared.key();
    }

    public String preferredName() {
        if (libraryManifest != null && libraryManifest.getName() != null
                && !libraryManifest.getName().isBlank()) {
            return libraryManifest.getName();
        }
        return id();
    }
}
