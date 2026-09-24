package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AssemblyCommandBuilder {
    private AssemblyCommandBuilder() { }

    public static String format(ManifestRootModel m, TargetPlatform platform) {
        return !NativeTools.specified(m.getAsmFormat()) || m.getAsmFormat().equalsIgnoreCase("auto")
                ? platform.objectFormat() : m.getAsmFormat().trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static List<String> build(NativeTools.Tool tool, TargetPlatform platform, ManifestRootModel m,
                                     Path project, Path source, Path output, Path dependencies,
                                     List<Path> extraIncludes) {
        return build(tool, platform, m, project, source, output, dependencies, extraIncludes, false);
    }

    public static List<String> build(NativeTools.Tool tool, TargetPlatform platform, ManifestRootModel m,
                                     Path project, Path source, Path output, Path dependencies,
                                     List<Path> extraIncludes, boolean preprocessed) {
        String format = format(m, platform);
        if (!format.equals("bin") && !format.equals(platform.objectFormat()))
            throw new IllegalArgumentException("asmFormat " + format + " incompativel com " + platform.triple());
        if (format.equals("bin") && !tool.kind().equals("nasm"))
            throw new IllegalArgumentException("asmFormat bin requer NASM; use type binary com asmFormat auto para converter apos o link");
        List<String> cmd = new ArrayList<>();
        cmd.add(tool.executable().toString());
        List<Path> includes = new ArrayList<>(extraIncludes);
        for (String inc : m.getIncludes()) includes.add(project.resolve(inc).normalize());
        switch (tool.kind()) {
            case "nasm" -> {
                cmd.addAll(List.of("-f", format));
                for (Path inc : includes) cmd.add("-I" + inc + java.io.File.separator);
                for (String define : m.getDefines()) cmd.add("-D" + stripDefine(define));
            }
            case "gas" -> {
                if (platform.x86() && !platform.windows()) cmd.add(platform.bits64() ? "--64" : "--32");
                for (Path inc : includes) cmd.addAll(List.of("-I", inc.toString()));
                if (!preprocessed) {
                    for (String define : m.getDefines()) {
                        String value = stripDefine(define);
                        cmd.add("--defsym"); cmd.add(value.contains("=") ? value : value + "=1");
                    }
                }
                if (dependencies != null) cmd.addAll(List.of("--MD", dependencies.toString()));
            }
            case "masm" -> {
                cmd.add("/nologo");
                if (!platform.bits64()) cmd.add("/coff");
                for (Path inc : includes) cmd.add("/I" + inc);
                for (String define : m.getDefines()) cmd.add("/D" + stripDefine(define));
            }
            default -> throw new IllegalArgumentException("Assembler inconhecido: " + tool.kind());
        }
        cmd.addAll(m.getAsmFlags());
        if (tool.kind().equals("masm")) cmd.addAll(List.of("/Fo" + output, "/c", source.toString()));
        else cmd.addAll(List.of("-o", output.toString(), source.toString()));
        return cmd;
    }

    public static List<String> nasmDependencies(NativeTools.Tool tool, TargetPlatform platform, ManifestRootModel m,
                                                Path project, Path source, Path output, Path dependencies,
                                                List<Path> extraIncludes) {
        List<String> cmd = build(tool, platform, m, project, source, output, null, extraIncludes);
        cmd.subList(cmd.size() - 3, cmd.size()).clear();
        cmd.addAll(List.of("-M", "-MF", dependencies.toString(), "-MQ", output.toString(), source.toString()));
        return cmd;
    }

    public static List<String> preprocess(Toolchain tc, ManifestRootModel m, Path project,
                                         Path source, Path output, Path depFile, List<Path> extraIncludes) {
        if (tc.isMsvc()) throw new IllegalArgumentException("Fontes GNU .S requerem pre-processador GCC/Clang");
        List<String> cmd = new ArrayList<>(List.of(tc.driver(false).toString(), "-E", "-x", "assembler-with-cpp"));
        if (NativeTools.specified(m.getPlatform()) && (tc.kind() == ToolchainKind.SYSTEM_CLANG || tc.kind() == ToolchainKind.BUNDLED_LLVM))
            cmd.add("--target=" + m.getPlatform());
        if (NativeTools.specified(m.getSysroot())) cmd.add("--sysroot=" + m.getSysroot());
        for (Path inc : extraIncludes) cmd.add("-I" + inc);
        for (String inc : m.getIncludes()) cmd.add("-I" + project.resolve(inc).normalize());
        for (String define : m.getDefines()) cmd.add("-D" + stripDefine(define));
        cmd.addAll(List.of("-MMD", "-MF", depFile.toString(), "-o", output.toString(), source.toString()));
        return cmd;
    }
    private static String stripDefine(String define) {
        return define.startsWith("-D") || define.startsWith("/D") ? define.substring(2) : define;
    }
}
