package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ToolchainDetector {

    private ToolchainDetector() {
    }

    public static List<Toolchain> detectAll() {
        List<Toolchain> out = new ArrayList<>();

        Path clang = ToolProbe.findOnPath("clang");
        Path clangxx = ToolProbe.findOnPath("clang++");
        if (clang != null || clangxx != null) {
            out.add(new Toolchain(ToolchainKind.SYSTEM_CLANG, clang, clangxx != null ? clangxx : clang));
        }

        Path gcc = ToolProbe.findOnPath("gcc");
        Path gxx = ToolProbe.findOnPath("g++");
        if (gcc != null || gxx != null) {
            ToolchainKind kind = ToolProbe.isWindows() ? ToolchainKind.MINGW : ToolchainKind.GCC;
            out.add(new Toolchain(kind, gcc, gxx != null ? gxx : gcc));
        }

        Path cl = ToolProbe.findOnPath("cl");
        if (cl != null) {
            out.add(new Toolchain(ToolchainKind.MSVC, cl, cl));
        }

        return out;
    }

    public static Toolchain resolve(ManifestRootModel manifest, String compilerOverride) {
        String cc = manifest == null ? null : manifest.getCCompiler();
        String cxx = manifest == null ? null : manifest.getCxxCompiler();
        if (notBlank(cc) || notBlank(cxx)) {
            Path ccPath = notBlank(cc) ? resolvePath(cc.trim()) : null;
            Path cxxPath = notBlank(cxx) ? resolvePath(cxx.trim()) : ccPath;
            return new Toolchain(ToolchainKind.CUSTOM, ccPath, cxxPath);
        }

        if (compilerOverride != null && !compilerOverride.isBlank()) {
            Path p = resolvePath(compilerOverride.trim());
            return new Toolchain(ToolchainKind.CUSTOM, p, p);
        }

        List<Toolchain> all = detectAll();
        return all.isEmpty() ? null : all.get(0);
    }

    private static Path resolvePath(String exe) {
        Path found = ToolProbe.findOnPath(exe);
        return found != null ? found : Path.of(exe);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
