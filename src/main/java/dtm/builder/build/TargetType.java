package dtm.builder.build;

import dtm.builder.manifest.model.ManifestTargetModel;

public enum TargetType {
    EXECUTABLE,
    SHARED,
    STATIC,
    OBJECT,
    BINARY;

    public static TargetType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EXECUTABLE;
        }
        return switch (raw.trim().toLowerCase()) {
            case ManifestTargetModel.TYPE_SHARED -> SHARED;
            case ManifestTargetModel.TYPE_STATIC -> STATIC;
            case "object" -> OBJECT;
            case "binary" -> BINARY;
            default -> EXECUTABLE;
        };
    }

    public boolean isLibrary() {
        return this == SHARED || this == STATIC;
    }
}
