package dtm.builder.build;

import dtm.builder.build.incremental.FileHasher;
import java.util.*;

public final class BuildEnvironment {
    private static final Set<String> RELEVANT = Set.of("PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "INCLUDE", "LIB", "LIBPATH",
            "CPATH", "C_INCLUDE_PATH", "CPLUS_INCLUDE_PATH", "OBJC_INCLUDE_PATH", "GCC_EXEC_PREFIX", "COMPILER_PATH", "LIBRARY_PATH",
            "SDKROOT", "MACOSX_DEPLOYMENT_TARGET", "IPHONEOS_DEPLOYMENT_TARGET", "SOURCE_DATE_EPOCH", "LANG", "LANGUAGE",
            "CL", "_CL_", "LINK", "_LINK_", "CCC_OVERRIDE_OPTIONS", "CCC_ADD_ARGS", "GCC_COMPARE_DEBUG", "TMP", "TEMP", "TMPDIR",
            "DEVELOPER_DIR", "VCINSTALLDIR", "VCTOOLSINSTALLDIR", "WINDOWSSDKDIR", "WINDOWSSDKVERSION");
    private BuildEnvironment() { }

    public static String signature(Map<String, String> inherited, Map<String, String> overrides) {
        TreeMap<String, String> relevant = new TreeMap<>();
        inherited.forEach((key, value) -> {
            String upper = key.toUpperCase(Locale.ROOT);
            if (RELEVANT.contains(upper) || upper.startsWith("LC_") || upper.startsWith("GCC_")
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
