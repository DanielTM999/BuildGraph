package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestTargetModel;

public enum TargetType {
    EXECUTABLE,
    SHARED,
    STATIC;

    public static TargetType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EXECUTABLE;
        }
        return switch (raw.trim().toLowerCase()) {
            case ManifestTargetModel.TYPE_SHARED -> SHARED;
            case ManifestTargetModel.TYPE_STATIC -> STATIC;
            default -> EXECUTABLE;
        };
    }

    public boolean isLibrary() {
        return this != EXECUTABLE;
    }
}
