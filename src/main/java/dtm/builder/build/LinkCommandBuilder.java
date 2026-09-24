package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Link-only commands never receive C standards, preprocessor options or assembler flags. */
public final class LinkCommandBuilder {
    private LinkCommandBuilder() { }
    public static List<String> build(Path tool, String kind, boolean driver, boolean cpp,
                                     TargetPlatform platform, ManifestRootModel m, Path project,
                                     boolean shared, List<Path> inputs, Path output,
                                     List<Path> libraryDirs, List<String> libraries) {
        List<String> cmd = new ArrayList<>();
        cmd.add(tool.toString());
        boolean msvc = kind.equals("msvc");
        if (driver && !msvc) {
            String name = NativeTools.name(tool);
            if (name.contains("clang")) {
                if (NativeTools.specified(m.getPlatform())) cmd.add("--target=" + platform.triple());
                if (cpp && !name.contains("++")) cmd.add("--driver-mode=g++");
            } else if (NativeTools.specified(m.getPlatform()) && platform.x86()) {
                cmd.add(platform.bits64() ? "-m64" : "-m32");
            }
            if (NativeTools.specified(m.getSysroot())) cmd.add("--sysroot=" + m.getSysroot());
        }
        if (msvc) {
            cmd.add("/nologo");
            if (driver) {
                cmd.add("/Fe:" + output);
                if (shared) cmd.add("/LD");
            }
        } else if (shared) cmd.add(platform.darwin() ? driver ? "-dynamiclib" : "-dylib" : "-shared");
        if (!driver && kind.equals("gnu") && platform.x86())
            cmd.addAll(List.of("-m", platform.windows() ? platform.bits64() ? "i386pep" : "i386pe"
                    : platform.bits64() ? "elf_x86_64" : "elf_i386"));
        if (!driver && kind.equals("darwin")) cmd.addAll(List.of("-arch", switch (platform.arch()) {
            case "aarch64" -> "arm64";
            case "x86" -> "i386";
            default -> platform.arch();
        }));
        for (Path input : inputs) cmd.add(input.toString());
        if (msvc && driver) cmd.add("/link");
        if (msvc) {
            if (!driver) cmd.add("/OUT:" + output);
            cmd.add("/PDB:" + output.resolveSibling(output.getFileName() + ".pdb"));
            cmd.add("/INCREMENTAL:NO");
            if (shared) {
                if (!driver) cmd.add("/DLL");
                String file = output.getFileName().toString().replaceFirst("(?i)\\.dll$", "");
                cmd.add("/IMPLIB:" + output.resolveSibling(file + ".lib"));
            }
            if (platform.arch().equals("x86_64")) cmd.add("/MACHINE:X64");
            else if (platform.arch().equals("x86")) cmd.add("/MACHINE:X86");
            else if (platform.arch().equals("aarch64")) cmd.add("/MACHINE:ARM64");
        }
        List<Path> dirs = new ArrayList<>(libraryDirs);
        for (String dir : m.getLibraryPaths()) dirs.add(project.resolve(dir).normalize());
        for (Path dir : dirs) cmd.add((msvc ? "/LIBPATH:" : "-L") + dir);
        for (String lib : libraries) cmd.add(msvc ? lib.endsWith(".lib") ? lib : lib + ".lib"
                : lib.startsWith("-l") ? lib : "-l" + lib);
        cmd.addAll(m.getLinkFlags());
        if (!msvc) cmd.addAll(List.of("-o", output.toString()));
        return cmd;
    }
}
