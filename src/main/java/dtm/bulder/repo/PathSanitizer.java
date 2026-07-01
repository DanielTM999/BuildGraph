package dtm.bulder.repo;

public final class PathSanitizer {

    private PathSanitizer() {
    }

    public static String sanitizeId(String id) {
        return sanitize(id, "unknown-id");
    }

    public static String sanitizeVersion(String version) {
        return sanitize(version, "unknown-version");
    }

    public static String sanitizeToken(String token) {
        return sanitize(token, "source");
    }

    public static String sanitizePackageFolderName(String name) {
        return sanitize(name, "package");
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String out = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        out = out.replaceAll("_+", "_");
        out = out.replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
        return out.isEmpty() ? fallback : out;
    }
}
