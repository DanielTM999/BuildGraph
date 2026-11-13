package dtm.builder.build;

import dtm.builder.build.incremental.FileHasher;
import java.util.*;

public final class BuildEnvironment {
    private static final Set<String> RELEVANT = Set.of("PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "INCLUDE", "LIB", "LIBPATH",
            "CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH", "OBJC_INCLUDE_PATH", "GCC_EXEC_PREFIX", "COMPILER_PATH", "LIBRARY_PATH",
            "SDKROOT", "MACOSX_DEPLOYMENT_TARGET", "IPHONEOS_DEPLOYMENT_TARGET", "SOURCE_DATE_EPOCH",
            "CL", "_CL_", "LINK", "_LINK_", "CCC_OVERRIDE_OPTIONS", "CCC_ADD_ARGS", "GCC_COMPARE_DEBUG",
            "DEVELOPER_DIR", "VCINSTALLDIR", "VCTOOLSINSTALLDIR", "WINDOWSSDKDIR", "WINDOWSSDKVERSION");
    private static final Set<String> TOOL_LOOKUP = Set.of("PATH", "PATHEXT", "SYSTEMROOT", "WINDIR");
    private static final Set<String> CHARSET = Set.of("LANG", "LC_ALL", "LC_CTYPE");
    private BuildEnvironment() { }

    public static String signature(Map<String, String> inherited, Map<String, String> overrides) {
        return signature(inherited, overrides, false, false);
    }

    public static String resolvedToolSignature(Map<String, String> inherited, Map<String, String> overrides, boolean windows) {
        return signature(inherited, overrides, true, windows);
    }

    private static String signature(Map<String, String> inherited, Map<String, String> overrides,
                                    boolean resolvedTools, boolean windows) {
        TreeMap<String, String> relevant = new TreeMap<>();
        inherited.forEach((key, value) -> {
            String upper = key.toUpperCase(Locale.ROOT);
            if (resolvedTools && (TOOL_LOOKUP.contains(upper) || windows && CHARSET.contains(upper))) return;
            if (RELEVANT.contains(upper) || CHARSET.contains(upper) || upper.startsWith("GCC_")
                    || upper.startsWith("CLANG_") || upper.startsWith("CCC_")) relevant.put(canonical(key), value);
        });

        if (overrides != null) overrides.forEach((key, value) -> relevant.put(canonical(key), value));
        return FileHasher.hashCommand(relevant.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }
    private static String canonical(String key) { return ToolProbe.isWindows() ? key.toUpperCase(Locale.ROOT) : key; }

    public static String customSignature(Map<String, String> inherited, Map<String, String> overrides) {
        var values = new TreeMap<String, String>();
        inherited.forEach((key, value) -> values.put(canonical(key), value));
        if (overrides != null) overrides.forEach((key, value) -> values.put(canonical(key), value));
        return FileHasher.hashCommand(values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }
}
