package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompileCommandBuilder {

    private CompileCommandBuilder() {
    }

    public static List<String> buildCompileCommand(CompileSpec spec) {
        Toolchain toolchain = spec.toolchain();
        Path driver = toolchain.driver(spec.cpp());

        if (toolchain.isMsvc()) {
            return buildMsvc(spec, driver);
        }
        return buildGccClang(spec, driver);
    }

    private static List<String> buildGccClang(CompileSpec spec, Path driver) {
        ManifestRootModel manifest = spec.manifest();
        List<String> cmd = new ArrayList<>();
        cmd.add(driver.toString());

        boolean clang = spec.toolchain().kind() == ToolchainKind.SYSTEM_CLANG
                || spec.toolchain().kind() == ToolchainKind.BUNDLED_LLVM;

        String driverName = driver.getFileName() == null ? "" : driver.getFileName().toString().toLowerCase();
        if (spec.cpp() && clang && driverName.startsWith("clang") && !driverName.contains("++")) {
            cmd.add("--driver-mode=g++");
        }

        cmd.add(StdFlags.toStdFlag(manifest, spec.cpp()));

        if (clang && notBlank(manifest.getPlatform())) {
            cmd.add("--target=" + manifest.getPlatform().trim());
        }
        if (notBlank(manifest.getSysroot())) {
            cmd.add("--sysroot=" + manifest.getSysroot().trim());
        }

        if (spec.isRelease()) {
            cmd.add("-O2");
            cmd.add("-DNDEBUG");
        } else {
            cmd.add("-O0");
            cmd.add("-g");
        }

        if (spec.library()) {
            cmd.add("-shared");
            if (!ToolProbe.isWindows()) {
                cmd.add("-fPIC");
            }
        }

        for (Path inc : spec.extraIncludeDirs()) {
            cmd.add("-I" + inc);
        }
        for (String inc : manifest.getIncludePaths()) {
            cmd.add("-I" + spec.projectPath().resolve(inc).normalize());
        }

        for (String define : manifest.getDefines()) {
            cmd.add(define.startsWith("-D") ? define : "-D" + define);
        }

        cmd.addAll(manifest.getCompileFlags());
        cmd.addAll(spec.extraFlags());

        for (Path source : spec.sources()) {
            cmd.add(source.toString());
        }

        for (Path libDir : spec.extraLibDirs()) {
            cmd.add("-L" + libDir);
        }
        for (String libDir : manifest.getLibraryPaths()) {
            cmd.add("-L" + spec.projectPath().resolve(libDir).normalize());
        }
        for (String lib : spec.extraLinkLibs()) {
            cmd.add(lib.startsWith("-l") ? lib : "-l" + lib);
        }

        cmd.addAll(manifest.getLinkFlags());

        cmd.add("-o");
        cmd.add(spec.artifact().toString());
        return cmd;
    }

    private static List<String> buildMsvc(CompileSpec spec, Path driver) {
        ManifestRootModel manifest = spec.manifest();
        List<String> cmd = new ArrayList<>();
        cmd.add(driver.toString());
        cmd.add("/nologo");
        if (spec.cpp()) {
            cmd.add("/EHsc");
            cmd.add("/std:" + StdFlags.normalize(firstNonBlank(manifest.getCxxStandard(),
                    manifest.getCompilerVersion(), "c++17")));
        }
        cmd.add(spec.isRelease() ? "/O2" : "/Od");
        if (spec.isRelease()) {
            cmd.add("/DNDEBUG");
        } else {
            cmd.add("/Zi");
        }

        for (Path inc : spec.extraIncludeDirs()) {
            cmd.add("/I" + inc);
        }
        for (String inc : manifest.getIncludePaths()) {
            cmd.add("/I" + spec.projectPath().resolve(inc).normalize());
        }
        for (String define : manifest.getDefines()) {
            cmd.add(define.startsWith("/D") ? define : "/D" + define);
        }
        cmd.addAll(manifest.getCompileFlags());
        cmd.addAll(spec.extraFlags());
        for (Path source : spec.sources()) {
            cmd.add(source.toString());
        }
        cmd.add("/Fe:" + spec.artifact());
        return cmd;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
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
