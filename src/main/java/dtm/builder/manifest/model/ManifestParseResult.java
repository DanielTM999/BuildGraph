package dtm.builder.manifest.model;

import java.util.ArrayList;
import java.util.List;

public class ManifestParseResult {

    private final ManifestRootModel manifest;
    private final List<ManifestDiagnostic> diagnostics;

    public ManifestParseResult(ManifestRootModel manifest, List<ManifestDiagnostic> diagnostics) {
        this.manifest = manifest;
        this.diagnostics = diagnostics != null ? diagnostics : new ArrayList<>();
    }

    public ManifestRootModel getManifest() {
        return manifest;
    }

    public List<ManifestDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean isOk() {
        for (ManifestDiagnostic d : diagnostics) {
            if (d.isError()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }
}
