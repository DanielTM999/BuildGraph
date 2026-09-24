package dtm.builder.integration;

import dtm.builder.build.*;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional real-tool checks; each absent backend is reported as skipped, never simulated. */
class AssemblyToolchainNativeIT {
    @TempDir Path project;
    private Path tool(String key, String name) {
        String configured = System.getProperty("buildgraph.it." + key, name);
        Path found = ToolProbe.findOnPath(configured);
        assumeTrue(found != null, "Ferramenta indisponivel: " + configured);
        return found;
    }
    private ManifestTargetModel target(String id, String type, String source) {
        var t = new ManifestTargetModel(); t.setId(id); t.setType(type); t.setSources(List.of(source)); return t;
    }
    private BuildResult build(ManifestRootModel m, List<String> logs) {
        return BuildExecutor.build(new BuildRequest(project, m, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", logs::add, 1, List.of(), true, null));
    }

    @Test void nasmBuildsBootSectorAndTracksIncludesWithoutCCompiler() throws Exception {
        Path nasm = tool("nasm", "nasm");
        Files.writeString(project.resolve("value.inc"), "%define VALUE 7\n");
        Files.writeString(project.resolve("boot.asm"), "%include \"value.inc\"\nbits 16\ndb VALUE\ntimes 510-($-$$) db 0\ndw 0xaa55\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(nasm.toString());
        var boot = target("boot", "binary", "boot.asm"); boot.setAsmFormat("bin"); m.setTargets(List.of(boot));
        List<String> logs = new ArrayList<>();
        BuildResult first = build(m, logs); assertTrue(first.success(), logs.toString());
        byte[] bytes = Files.readAllBytes(project.resolve("build/boot.bin"));
        assertEquals(512, bytes.length); assertEquals(7, bytes[0]); assertEquals(0x55, Byte.toUnsignedInt(bytes[510]));
        assertEquals(0xaa, Byte.toUnsignedInt(bytes[511]));
        logs.clear(); assertTrue(build(m, logs).success(), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("+ ")), logs.toString());
        Files.writeString(project.resolve("value.inc"), "%define VALUE 9\n");
        assertTrue(build(m, logs).success(), logs.toString());
        assertEquals(9, Files.readAllBytes(project.resolve("build/boot.bin"))[0]);
    }

    @Test void nasmProducesElfAndCoffRegardlessOfHost() throws Exception {
        Path nasm = tool("nasm", "nasm");
        Files.writeString(project.resolve("helper.asm"), "global helper\nsection .text\nhelper: ret\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(nasm.toString());
        var elf = target("elf", "object", "helper.asm"); elf.setPlatform("x86_64-linux-gnu");
        var coff = target("coff", "object", "helper.asm"); coff.setPlatform("x86_64-pc-windows-msvc");
        m.setTargets(List.of(elf, coff)); List<String> logs = new ArrayList<>();
        assertTrue(build(m, logs).success(), logs.toString());
        ObjectFormatValidator.validate(project.resolve("build/elf.o"), TargetPlatform.resolve(elf.getPlatform(), null));
        ObjectFormatValidator.validate(project.resolve("build/coff.obj"), TargetPlatform.resolve(coff.getPlatform(), null));
    }

    @Test void buildsAllThreeLanguagesIntoFreestandingBinary() throws Exception {
        Path nasm = tool("nasm", "nasm"), clang = tool("clang", "clang"), linker = tool("ld", "ld.lld"), objcopy = tool("objcopy", "llvm-objcopy");
        Files.writeString(project.resolve("entry.c"), "extern int asm_value(void); extern int cpp_value(void); void _start(void) { volatile int n = asm_value() + cpp_value(); (void)n; }\n");
        Files.writeString(project.resolve("part.cpp"), "extern \"C\" int cpp_value() { return 2; }\n");
        Files.writeString(project.resolve("part.asm"), "global asm_value\nsection .text\nasm_value: mov eax, 3\nret\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(nasm.toString());
        m.setCCompiler(clang.toString()); m.setCxxCompiler(clang.toString());
        m.setPlatform("x86_64-linux-gnu"); m.setLinker(linker.toString()); m.setLinkerKind("gnu");
        m.setObjcopy(objcopy.toString()); m.setCompileFlags(List.of("-ffreestanding", "-fno-stack-protector"));
        m.setLinkFlags(List.of("-e", "_start"));
        var kernel = target("kernel", "binary", "entry.c"); kernel.setSources(List.of("entry.c", "part.cpp", "part.asm")); kernel.setLinkMode("linker");
        m.setTargets(List.of(kernel)); List<String> logs = new ArrayList<>();
        assertTrue(build(m, logs).success(), logs.toString());
        assertTrue(Files.size(project.resolve("build/kernel.bin")) > 0);
        logs.clear(); assertTrue(build(m, logs).success(), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("+ ")), logs.toString());
    }

    @Test void gnuAssemblerSupportsConfiguredCrossArchitecture() throws Exception {
        Path assembler = tool("gas", "as");
        String platform = System.getProperty("buildgraph.it.gasPlatform", TargetPlatform.host().triple());
        Files.writeString(project.resolve("helper.s"), ".global helper\n.text\nhelper: ret\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(assembler.toString()); m.setAsmKind("gas"); m.setPlatform(platform);
        m.setTargets(List.of(target("helper", "object", "helper.s")));
        List<String> logs = new ArrayList<>(); assertTrue(build(m, logs).success(), logs.toString());
        Path output = project.resolve("build/" + TargetPlatform.resolve(platform, null).fileName("helper", TargetType.OBJECT, false));
        ObjectFormatValidator.validate(output, TargetPlatform.resolve(platform, null));
        logs.clear(); assertTrue(build(m, logs).success(), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("+ ")), logs.toString());
    }

    @Test void masmBuildsCoffObject() throws Exception {
        Path masm = tool("masm", "ml64");
        Files.writeString(project.resolve("helper.asm"), ".code\nhelper PROC\nxor eax, eax\nret\nhelper ENDP\nEND\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(masm.toString()); m.setAsmKind("masm"); m.setPlatform("x86_64-pc-windows-msvc");
        m.setTargets(List.of(target("helper", "object", "helper.asm")));
        List<String> logs = new ArrayList<>(); assertTrue(build(m, logs).success(), logs.toString());
        ObjectFormatValidator.validate(project.resolve("build/helper.obj"), TargetPlatform.resolve(m.getPlatform(), null));
    }

    @Test void gnuPreprocessesUppercaseAssemblyAndTracksHeaderChanges() throws Exception {
        Path assembler = tool("gas", "as");
        String name = assembler.getFileName().toString();
        String compilerName = name.replaceFirst("as(?=\\.exe$|$)", "gcc");
        Path beside = assembler.resolveSibling(compilerName);
        Path compiler = tool("gasCompiler", Files.isRegularFile(beside) ? beside.toString() : "gcc");
        String platform = System.getProperty("buildgraph.it.gasPlatform", TargetPlatform.host().triple());
        Files.writeString(project.resolve("value.h"), "#define VALUE 1\n");
        Files.writeString(project.resolve("helper.S"), "#include \"value.h\"\n.global helper\n.text\nhelper: .word VALUE\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(assembler.toString()); m.setAsmKind("gas");
        m.setCCompiler(compiler.toString()); m.setPlatform(platform);
        m.setTargets(List.of(target("helper", "object", "helper.S")));
        List<String> logs = new ArrayList<>(); assertTrue(build(m, logs).success(), logs.toString());
        logs.clear(); assertTrue(build(m, logs).success(), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("+ ")), logs.toString());
        Files.writeString(project.resolve("value.h"), "#define VALUE 2\n");
        assertTrue(build(m, logs).success(), logs.toString());
        assertTrue(logs.stream().anyMatch(s -> s.contains("assembler-with-cpp")), logs.toString());
    }

    @Test void packageAndSelectiveCleanWorkThroughTheCli() throws Exception {
        Path nasm = tool("nasm", "nasm");
        Files.writeString(project.resolve("boot.asm"), "bits 16\ndb 0x55, 0xaa\n");
        Files.writeString(project.resolve("other.asm"), "invalid source\n");
        var m = new ManifestRootModel(); m.setAsmCompiler(nasm.toString());
        var boot = target("boot", "binary", "boot.asm"); boot.setAsmFormat("bin");
        var other = target("other", "object", "other.asm"); other.setAsmKind("unavailable");
        m.setTargets(List.of(boot, other));
        dtm.builder.manifest.ManifestParser.write(m, project.resolve("Manifest.json"));
        Path jar = Path.of("target/BuildGraph.jar").toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Execute pela fase verify");
        Path java = Path.of(System.getProperty("java.home"), "bin", ToolProbe.isWindows() ? "java.exe" : "java");
        List<String> logs = new ArrayList<>();
        int built = ProcessRunner.run(List.of(java.toString(), "-jar", jar.toString(), "package", "--target", "boot"), project, null, logs::add);
        assertEquals(0, built, logs.toString()); assertTrue(Files.isRegularFile(project.resolve("build/boot.bin")));
        Files.writeString(project.resolve("build/other.o"), "preserved");
        int cleaned = ProcessRunner.run(List.of(java.toString(), "-jar", jar.toString(), "clean", "--target", "boot"), project, null, logs::add);
        assertEquals(0, cleaned, logs.toString()); assertFalse(Files.exists(project.resolve("build/boot.bin")));
        assertTrue(Files.exists(project.resolve("build/other.o")));
    }
}
