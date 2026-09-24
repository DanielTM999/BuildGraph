package dtm.builder.build;

import dtm.builder.UserArgs;
import dtm.builder.build.graph.TargetSelection;
import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeBuildTest {
    @TempDir Path project;

    static final class FakeTools implements ProcessExecutor {
        final List<List<String>> commands = new ArrayList<>();
        @Override public int run(List<String> cmd, Path cwd, java.util.Map<String,String> env, java.util.function.Consumer<String> out) {
            commands.add(List.copyOf(cmd));
            try {
                Path output = null;
                if (cmd.contains("-o")) output = Path.of(cmd.get(cmd.indexOf("-o") + 1));
                for (String arg : cmd) {
                    if (arg.startsWith("/Fo")) output = Path.of(arg.substring(3).replaceFirst("^:", ""));
                    if (arg.startsWith("/OUT:") || arg.startsWith("/Fe:")) output = Path.of(arg.substring(arg.indexOf(':') + 1));
                }
                if (cmd.contains("rcs")) output = Path.of(cmd.get(cmd.indexOf("rcs") + 1));
                if (cmd.contains("binary")) output = Path.of(cmd.getLast());
                if (output != null) {
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "artifact-" + commands.size());
                }
                for (String flag : List.of("-MF", "-MD", "--MD")) if (cmd.contains(flag)) {
                    Path dep = Path.of(cmd.get(cmd.indexOf(flag) + 1));
                    StringBuilder body = new StringBuilder(escape(output) + ":");
                    for (String arg : cmd) {
                        if (arg.matches(".*\\.(c|cpp|asm|s|S)$") && Files.exists(Path.of(arg))) body.append(' ').append(escape(Path.of(arg)));
                    }
                    if (Files.exists(cwd.resolve("include.inc"))) body.append(' ').append(escape(cwd.resolve("include.inc")));
                    Files.writeString(dep, body + "\n");
                }
                return 0;
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        private String escape(Path p) { return p.toString().replace('\\', '/').replace(" ", "\\ "); }
    }

    ManifestRootModel manifest() {
        ManifestRootModel m = new ManifestRootModel();
        m.setName("sample");
        m.setCCompiler("fake-gcc"); m.setCxxCompiler("fake-g++");
        m.setAsmCompiler("fake-nasm"); m.setAsmKind("nasm");
        m.setLinker("fake-ld"); m.setLinkerKind("gnu");
        m.setArchiver("fake-ar"); m.setObjcopy("fake-objcopy");
        m.setPlatform("x86_64-linux-gnu");
        return m;
    }
    ManifestTargetModel target(String id, String type, String... sources) throws Exception {
        ManifestTargetModel t = new ManifestTargetModel(); t.setId(id); t.setType(type); t.setSources(List.of(sources));
        for (String source : sources) Files.writeString(project.resolve(source), "source");
        return t;
    }
    BuildRequest request(ManifestRootModel m, FakeTools tools, String... selected) {
        return new BuildRequest(project, m, null, BuildSystem.MANIFEST, project.resolve("packages"),
                project.resolve("build"), "Debug", ignored -> {}, 1, List.of(selected), true, tools);
    }

    @Test void buildsAllSevenLanguageCombinationsAndSelectsDriver() throws Exception {
        for (int mask = 1; mask < 8; mask++) {
            ManifestRootModel m = manifest();
            List<String> sources = new ArrayList<>();
            if ((mask & 1) != 0) sources.add("part" + mask + ".c");
            if ((mask & 2) != 0) sources.add("part" + mask + ".cpp");
            if ((mask & 4) != 0) sources.add("part" + mask + ".asm");
            var target = target("combo" + mask, "executable", sources.toArray(String[]::new));
            target.setCompileFlags(List.of("-DC_ONLY")); target.setAsmFlags(List.of("-DASM_ONLY"));
            m.setTargets(List.of(target)); FakeTools tools = new FakeTools();
            BuildResult result = BuildExecutor.build(request(m, tools));
            assertTrue(result.success(), result.message());
            String driver = tools.commands.getLast().getFirst();
            assertTrue(driver.endsWith((mask & 2) != 0 ? "fake-g++" : (mask & 1) != 0 ? "fake-gcc" : "fake-ld"), driver);
            for (var command : tools.commands) {
                if (command.getFirst().endsWith("fake-nasm")) {
                    assertTrue(command.contains("-DASM_ONLY")); assertFalse(command.contains("-DC_ONLY"));
                } else assertFalse(command.contains("-DASM_ONLY"));
            }
            assertFalse(tools.commands.getLast().contains("-DC_ONLY"));
        }
    }

    @Test void selectedChainSkipsUnrelatedToolsAndDeduplicatesArchivedObjects() throws Exception {
        ManifestRootModel m = manifest();
        var asm = target("asm", "object", "helper.asm");
        var core = target("core", "static", "core.cpp"); core.setDependsOn(List.of("asm"));
        var app = target("app", "executable", "main.c"); app.setDependsOn(List.of("core"));
        var other = target("other", "executable", "bad.asm"); other.setAsmKind("invalid");
        m.setTargets(List.of(asm, core, app, other)); FakeTools tools = new FakeTools();
        BuildResult result = BuildExecutor.build(request(m, tools, "app"));
        assertTrue(result.success(), result.message());
        assertEquals(5, tools.commands.size());
        assertTrue(tools.commands.getLast().getFirst().endsWith("fake-g++"));
        assertTrue(tools.commands.getLast().stream().anyMatch(a -> a.endsWith("libcore.a")));
        assertFalse(tools.commands.getLast().stream().anyMatch(a -> a.endsWith("asm.o")));
        assertFalse(Files.exists(project.resolve("build/other")));
    }

    @Test void asmCacheTracksIncludesToolFormatAndLinkScripts() throws Exception {
        ManifestRootModel m = manifest(); var app = target("app", "executable", "main.asm");
        m.setTargets(List.of(app));
        Files.writeString(project.resolve("include.inc"), "v1");
        Files.writeString(project.resolve("layout.ld"), "script1");
        app.setLinkDependencies(List.of("layout.ld"));
        FakeTools tools = new FakeTools(); var req = request(m, tools);
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        Files.writeString(project.resolve("include.inc"), "v2 changed");
        assertTrue(BuildExecutor.build(req).success()); assertEquals(4, tools.commands.size());
        Files.writeString(project.resolve("layout.ld"), "script2 changed");
        assertTrue(BuildExecutor.build(req).success()); assertEquals(5, tools.commands.size());
        app.setAsmFlags(List.of("-DCHANGED"));
        assertTrue(BuildExecutor.build(req).success()); assertEquals(7, tools.commands.size());
    }

    @Test void rawNasmNeedsNoCompilerAndConvertedBinaryUsesObjcopy() throws Exception {
        ManifestRootModel m = manifest(); m.setCCompiler(null); m.setCxxCompiler(null);
        var boot = target("boot", "binary", "boot.asm"); boot.setAsmFormat("bin"); boot.setOutputName("boot/sector.bin");
        m.setTargets(List.of(boot)); FakeTools tools = new FakeTools();
        assertTrue(BuildExecutor.build(request(m, tools)).success()); assertEquals(1, tools.commands.size());
        assertTrue(Files.exists(project.resolve("build/boot/sector.bin")));
        boot.setAsmFormat("auto");
        assertTrue(BuildExecutor.build(request(m, tools)).success()); assertEquals(4, tools.commands.size());
        assertTrue(tools.commands.getLast().contains("binary"));
    }

    @Test void platformFallbackAndDestinationNamesAreIndependentOfHost() throws Exception {
        List<String> warnings = new ArrayList<>();
        assertEquals(TargetPlatform.host(), TargetPlatform.resolve("not a platform", warnings::add));
        assertEquals(1, warnings.size());
        for (String triple : List.of("aarch64-linux-gnu", "riscv64-unknown-elf", "mipsel-none-elf", "customcpu-none-elf"))
            assertEquals(triple, TargetPlatform.resolve(triple, null).triple());
        var p = TargetPlatform.resolve("aarch64-linux-gnu", null);
        assertEquals("app", p.fileName("app", TargetType.EXECUTABLE, false));
        ManifestRootModel m = manifest(); m.setPlatform("aarch64-linux-gnu"); m.setTargets(List.of(target("bad", "object", "bad.asm")));
        FakeTools tools = new FakeTools();
        assertFalse(BuildExecutor.build(request(m, tools)).success()); assertTrue(tools.commands.isEmpty());
    }

    @Test void manifestsRoundTripAndProfilesMergeNativeOptions() throws Exception {
        var parsed = ManifestParser.readManifest("""
                {"asmCompiler":"nasm", "asmFlags":["-g"], "linker":"ld", "activeProfile":"release",
                 "targets":[{"id":"boot","type":"binary","asmFormat":"bin","sources":["boot.asm"]}],
                 "profiles":{"release":{"asmFlags":["-Ox"],"targets":[{"id":"boot","platform":"x86_64-none-elf","outputName":"sector.bin"}]}}}
                """, false);
        assertTrue(parsed.isOk());
        var effective = ManifestProfiles.effective(parsed.getManifest());
        assertEquals(List.of("-g", "-Ox"), effective.getAsmFlags());
        assertEquals("sector.bin", effective.getTargets().getFirst().getOutputName());
        for (boolean xml : List.of(false, true)) {
            String written = ManifestParser.writeString(effective, xml);
            var round = ManifestParser.readManifest(written, xml);
            assertTrue(round.isOk(), written);
            assertEquals(effective.getAsmFlags(), round.getManifest().getAsmFlags());
            assertEquals("bin", round.getManifest().getTargets().getFirst().getAsmFormat());
        }
    }

    @Test void packageAliasAndUnknownTargetNeverBuildEverything() throws Exception {
        var args = new UserArgs(new String[]{"package", "--target", "app"});
        assertFalse(args.hasProjectPath()); assertTrue(args.hasBuild()); assertEquals(List.of("app"), args.getTargets());
        assertTrue(new UserArgs(new String[]{"package", "--target", ","}).isInvalidCommand());
        ManifestRootModel m = manifest(); m.setTargets(List.of(target("one", "object", "one.asm")));
        FakeTools tools = new FakeTools();
        assertFalse(BuildExecutor.build(request(m, tools, "missing")).success()); assertTrue(tools.commands.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TargetSelection.resolve(m, project, false, List.of("missing")));
    }

    @Test void assemblerAdaptersKeepTheirOwnSyntax() {
        ManifestRootModel m = manifest(); m.setDefines(List.of("VALUE=3")); m.setIncludes(List.of("with space"));
        var win = TargetPlatform.resolve("x86_64-pc-windows-msvc", null);
        var masm = AssemblyCommandBuilder.build(new NativeTools.Tool(Path.of("ml64"), "masm"), win, m, project,
                Path.of("source.asm"), Path.of("a.obj"), Path.of("a.d"), List.of());
        assertTrue(masm.contains("/DVALUE=3")); assertFalse(masm.contains("/coff"));
        assertTrue(masm.indexOf("/Foa.obj") < masm.indexOf("/c"));
        var arm = TargetPlatform.resolve("aarch64-linux-gnu", null);
        var gas = AssemblyCommandBuilder.build(new NativeTools.Tool(Path.of("aarch64-linux-gnu-as"), "gas"), arm, m, project,
                Path.of("a.s"), Path.of("a.o"), Path.of("a.d"), List.of());
        assertTrue(gas.contains("--MD")); assertTrue(gas.contains("--defsym")); assertFalse(gas.contains("--64"));
    }

    @Test void uppercaseAssemblyIsPreprocessedAndTracksBothDependencyFiles() throws Exception {
        var m = manifest(); m.setAsmKind("gas"); m.setAsmCompiler("fake-as");
        m.setDefines(List.of("TEXT=\"value\""));
        m.setTargets(List.of(target("preprocessed", "object", "main.S")));
        Files.writeString(project.resolve("include.inc"), "first");
        FakeTools tools = new FakeTools(); var req = request(m, tools);
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        assertTrue(tools.commands.getFirst().contains("assembler-with-cpp"));
        assertFalse(tools.commands.getLast().contains("--defsym"));
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        Files.writeString(project.resolve("include.inc"), "second version");
        assertTrue(BuildExecutor.build(req).success()); assertEquals(4, tools.commands.size());
    }

    @Test void masmAndMsvcCanShareATargetWithoutMixingArguments() throws Exception {
        var m = manifest(); m.setCCompiler("cl"); m.setCxxCompiler("cl");
        m.setAsmCompiler("ml64"); m.setAsmKind("masm"); m.setPlatform("x86_64-pc-windows-msvc");
        m.setAsmFlags(List.of("/DASM_ONLY")); m.setCompileFlags(List.of("/DC_ONLY"));
        m.setLinkFlags(List.of("/SUBSYSTEM:CONSOLE"));
        m.setTargets(List.of(target("msvc", "executable", "main.cpp", "support.asm")));
        FakeTools tools = new FakeTools(); BuildResult result = BuildExecutor.build(request(m, tools));
        assertTrue(result.success(), result.message());
        var asm = tools.commands.stream().filter(c -> NativeTools.name(Path.of(c.getFirst())).equals("ml64")).findFirst().orElseThrow();
        assertTrue(asm.contains("/DASM_ONLY")); assertFalse(asm.contains("/DC_ONLY"));
        var link = tools.commands.getLast(); assertTrue(link.contains("/link"));
        assertTrue(link.contains("/SUBSYSTEM:CONSOLE")); assertFalse(link.contains("/DC_ONLY"));
    }

    @Test void explicitLinkerCanBeChosenForCppAndMissingLinkInputsFail() throws Exception {
        var m = manifest(); var app = target("app", "executable", "main.cpp"); app.setLinkMode("linker");
        m.setTargets(List.of(app)); FakeTools tools = new FakeTools();
        assertTrue(BuildExecutor.build(request(m, tools)).success());
        assertTrue(tools.commands.getLast().getFirst().endsWith("fake-ld"));
        app.setLinkDependencies(List.of("missing.ld"));
        assertFalse(BuildExecutor.build(request(m, tools)).success());
    }

    @Test void importLibraryCollisionAndUnsafeOutputAreRejectedBeforeProcesses() throws Exception {
        var m = manifest(); m.setPlatform("x86_64-pc-windows-msvc"); m.setLinkerKind("msvc");
        var dll = target("dll", "shared", "dll.asm"); dll.setName("common");
        var lib = target("lib", "static", "lib.asm"); lib.setName("common");
        m.setTargets(List.of(dll, lib)); FakeTools tools = new FakeTools();
        assertFalse(BuildExecutor.build(request(m, tools)).success()); assertTrue(tools.commands.isEmpty());
        m.setTargets(List.of(dll)); dll.setOutputName("../outside.dll");
        assertFalse(BuildExecutor.build(request(m, tools)).success()); assertTrue(tools.commands.isEmpty());
    }

    @Test void assemblyOnlyCompilationDatabaseNeedsNoCCompiler() throws Exception {
        var m = manifest(); m.setTargets(List.of(target("boot", "object", "boot.asm")));
        assertEquals("[]", dtm.builder.integration.CompilationDatabaseGenerator.generate(project, m,
                null, project.resolve("packages"), project.resolve("build"), "Debug"));
    }

    @Test void cppObjectInsideAssemblyLibraryStillSelectsCppLinkDriver() throws Exception {
        var m = manifest(); var cpp = target("cpp", "object", "value.cpp");
        var lib = target("asmLib", "static", "stub.asm"); lib.setDependsOn(List.of("cpp"));
        var app = target("app", "executable", "main.c"); app.setDependsOn(List.of("asmLib"));
        m.setTargets(List.of(cpp, lib, app)); FakeTools tools = new FakeTools();
        var result = BuildExecutor.build(request(m, tools, "app"));
        assertTrue(result.success(), result.message());
        assertTrue(tools.commands.getLast().getFirst().endsWith("fake-g++"));
        assertFalse(tools.commands.getLast().stream().anyMatch(s -> s.endsWith("cpp.o")));
    }

    @Test void distinctIdsKeepDistinctIncrementalStates() throws Exception {
        var m = manifest(); m.setTargets(List.of(target("part__one", "object", "first.asm"),
                target("part_one", "object", "second.asm")));
        var tools = new FakeTools(); var req = request(m, tools);
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        assertTrue(BuildExecutor.build(req).success()); assertEquals(2, tools.commands.size());
        assertTrue(Files.exists(project.resolve("build/.buildgraph-state/part__one.json")));
        assertTrue(Files.exists(project.resolve("build/.buildgraph-state/part_one.json")));
    }
}
