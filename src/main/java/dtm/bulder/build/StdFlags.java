package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

public final class StdFlags {

    private StdFlags() {
    }

    public static String toStdFlag(ManifestRootModel manifest, boolean cpp) {
        String raw;
        if (cpp) {
            raw = firstNonBlank(manifest.getCxxStandard(), manifest.getCompilerVersion(), "c++17");
        } else {
            raw = firstNonBlank(manifest.getCStandard(), manifest.getCompilerVersion(), "c11");
        }
        return "-std=" + normalize(raw.trim());
    }

    static String normalize(String std) {
        String lower = std.toLowerCase();
        if (lower.startsWith("cpp")) {
            return "c++" + lower.substring(3);
        }
        if (lower.startsWith("cxx")) {
            return "c++" + lower.substring(3);
        }
        return std;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
