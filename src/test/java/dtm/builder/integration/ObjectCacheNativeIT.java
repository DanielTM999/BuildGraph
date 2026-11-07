package dtm.builder.integration;

import dtm.builder.build.*;
import dtm.builder.build.cache.SharedObjectCache;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.SafeZipExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class ObjectCacheNativeIT {
    @TempDir Path temp;
    private List<String> logs;

    private ManifestRootModel project(Path root, boolean mapped) throws Exception {
        Files.createDirectories(root.resolve("src")); Files.createDirectories(root.resolve("include"));
        Files.writeString(root.resolve("include/shared.h"), "#ifndef EXPECTED\n#define EXPECTED 5\n#endif\nint answer();\n");
        Files.writeString(root.resolve("src/main.cpp"), "#include \"shared.h\"\nint main(){return answer()!=EXPECTED;}\n");
        Files.writeString(root.resolve("src/answer.cpp"), "#include \"shared.h\"\nint answer(){return EXPECTED+0;}\n");
        var m = new ManifestRootModel(); m.setName("app"); m.setSources(List.of("src")); m.setIncludes(List.of("include"));
        m.setCCompiler(System.getProperty("buildgraph.it.cCompiler")); m.setCxxCompiler(System.getProperty("buildgraph.it.cxxCompiler"));
        assumeTrue(m.getCxxCompiler() != null && !m.getCxxCompiler().equals("cl"), "MSVC conservatively bypasses shared caching");
        if (mapped) m.setCompileFlags(List.of("-ffile-prefix-map=" + root + "=."));
        return m;
    }
    private BuildResult build(Path root, ManifestRootModel manifest) {
        logs = new CopyOnWriteArrayList<>();
        Toolchain toolchain = ToolchainDetector.resolve(manifest, null, null, null, root);
        var request = new BuildRequest(root, manifest, toolchain, BuildSystem.MANIFEST, root.resolve("packages"), root.resolve("build"),
                "Debug", logs::add, 4, List.of(), true, null,
                new BuildOptions(512L << 20, 64L << 20, temp.resolve("cache"), 128L << 20, true, true));
        return BuildExecutor.build(request);
    }
    private long count(String metric) {
        String prefix = "[profile] " + metric + "=";
        return logs.stream().filter(line -> line.startsWith(prefix)).mapToLong(line -> Long.parseLong(line.substring(prefix.length()))).sum();
    }
    private void succeeds(Path root, ManifestRootModel m, int exit) throws Exception {
        BuildResult result = build(root, m); assertTrue(result.success(), () -> result + "\n" + String.join("\n", logs));
        Path executable = root.resolve("build/app" + (ToolProbe.isWindows() ? ".exe" : ""));
        Process process = new ProcessBuilder(executable.toString()).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(exit, process.exitValue());
    }
    private void clean(Path root) throws Exception { SafeZipExtractor.deleteTree(root.resolve("build")); }

    @Test void cleanRestoreAndSourceHeaderFlagsEnvironmentAndCorruptionInvalidateCorrectly() throws Exception {
        Path root = temp.resolve("project"); var m = project(root, false);
        succeeds(root, m, 0); assertEquals(2, count("compiled")); assertEquals(2, count("cache_misses"));
        succeeds(root, m, 0); assertEquals(0, count("compiled")); assertEquals(2, count("local_reused"));
        clean(root); succeeds(root, m, 0); assertEquals(2, count("cache_hits")); assertEquals(0, count("compiled"));
        Path source = root.resolve("src/answer.cpp"); var time = Files.getLastModifiedTime(source);
        Files.writeString(source, Files.readString(source).replace("EXPECTED+0", "EXPECTED+1")); Files.setLastModifiedTime(source, time);
        clean(root); succeeds(root, m, 1); assertEquals(1, count("compiled")); assertEquals(1, count("cache_hits"));
        Path header = root.resolve("include/shared.h"); time = Files.getLastModifiedTime(header);
        Files.writeString(header, Files.readString(header).replace("EXPECTED 5", "EXPECTED 6")); Files.setLastModifiedTime(header, time);
        clean(root); succeeds(root, m, 1); assertEquals(2, count("compiled")); assertEquals(0, count("cache_hits"));
        m.setCompileFlags(List.of("-DEXPECTED=8")); clean(root); succeeds(root, m, 1); assertEquals(2, count("compiled"));
        m.setEnv(Map.of("BUILDGRAPH_TEST_VARIANT", "new")); clean(root); succeeds(root, m, 1); assertEquals(2, count("compiled"));
        try (var entries = Files.walk(temp.resolve("cache"))) {
            for (Path entry : entries.filter(p -> p.toString().endsWith(".entry")).toList()) Files.writeString(entry, "corrupt");
        }
        clean(root); succeeds(root, m, 1); assertEquals(2, count("compiled")); assertEquals(0, count("cache_hits"));
        clean(root); succeeds(root, m, 1); assertEquals(2, count("cache_hits"), "Corrupt entries should have been healed");
    }

    @Test void compatibleMappedCheckoutsCanShareObjectsWhileUnmappedPathsStayDistinct() throws Exception {
        Path a = temp.resolve("a"), b = temp.resolve("b"), c = temp.resolve("c");
        var ma = project(a, true); var mb = project(b, true); var mc = project(c, false);
        succeeds(a, ma, 0); assertEquals(2, count("compiled"));
        succeeds(b, mb, 0); assertEquals(2, count("cache_hits")); assertEquals(0, count("compiled"));
        succeeds(c, mc, 0); assertEquals(0, count("cache_hits")); assertEquals(2, count("compiled"));
    }

    @Test void newlyCreatedShadowHeaderIsNotHiddenByTheIncrementalState() throws Exception {
        Path root = temp.resolve("shadow"); var m = project(root, false);
        succeeds(root, m, 0);
        Files.writeString(root.resolve("src/shared.h"), "#define EXPECTED 7\nint answer();\n");
        succeeds(root, m, 0); assertEquals(2, count("compiled")); assertEquals(0, count("local_reused"));
    }

    @Test void inlineAssemblerExternalFilesAreNeverReusedAsOrdinaryCDependencies() throws Exception {
        Path root = temp.resolve("inline-assembly"); var m = project(root, false);
        m.setSources(List.of("src/main.cpp"));
        Files.writeString(root.resolve("payload.bin"), "A");
        Files.writeString(root.resolve("src/main.cpp"), """
                extern "C" const unsigned char payload[];
                asm(".data\\n.globl payload\\npayload:\\n.incbin \\"payload.bin\\"\\n.text");
                int main(){return payload[0]!=65;}
                """);
        succeeds(root, m, 0); assertEquals(0, count("cache_hits"));
        Files.writeString(root.resolve("payload.bin"), "B");
        succeeds(root, m, 1); assertEquals(1, count("compiled")); assertEquals(0, count("local_reused"));
    }

    @Test void uppercaseCExtensionPreservesTheGccCppLanguageWhenCaching() throws Exception {
        assumeFalse(System.getProperty("buildgraph.it.cCompiler", "").contains("clang"), "Legacy C standard fallback differs in Clang");
        Path root = temp.resolve("uppercase"); var m = project(root, false);
        Files.writeString(root.resolve("src/main.C"), "namespace sample { constexpr int value(){return 0;} } int main(){return sample::value();}");
        m.setSources(List.of("src/main.C"));
        succeeds(root, m, 0); assertEquals(1, count("compiled"));
        clean(root); succeeds(root, m, 0); assertEquals(1, count("cache_hits"));
    }
}
