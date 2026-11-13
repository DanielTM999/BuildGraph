package dtm.builder.integration;

import dtm.builder.build.*;
import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LargeProjectNativeIT {
    @TempDir Path temp;
    @Test void compilesAndLinksAThousandUnitsWithSpacesInPathsAndRunsTheBinary() throws Exception {
        Path root = Files.createDirectories(temp.resolve("many units"));
        Path source = Files.createDirectories(root.resolve("src"));
        StringBuilder main = new StringBuilder(); int expected = 0;
        for (int i = 0; i < 1000; i++) {
            Files.writeString(source.resolve("u" + i + ".c"), "int f" + i + "(void){return " + (i % 123) + ";}\n");
            main.append("int f").append(i).append("(void);\n"); expected += i % 123;
        }
        main.append("int main(void){int value=0;\n");
        for (int i = 0; i < 1000; i++) main.append("value+=f").append(i).append("();\n");
        main.append("return value!=").append(expected).append(";}\n");
        Files.writeString(source.resolve("main.c"), main);
        var manifest = new ManifestRootModel(); manifest.setName("app"); manifest.setSources(List.of("src"));
        manifest.setCCompiler(System.getProperty("buildgraph.it.cCompiler")); manifest.setCxxCompiler(System.getProperty("buildgraph.it.cxxCompiler"));
        var toolchain = ToolchainDetector.resolve(manifest, null, null, null, root);
        var log = new ConcurrentLinkedQueue<String>();
        BuildRequest request = new BuildRequest(root, manifest, toolchain, BuildSystem.MANIFEST, root.resolve("packages"), root.resolve("build"),
                "Release", log::add, 4, List.of(), true, null,
                new BuildOptions(256L << 20, 32L << 20, temp.resolve("cache"), 0, false, true, 128L << 20));
        BuildResult result = BuildExecutor.build(request);
        assertTrue(result.success(), () -> result + "\n" + String.join("\n", log));
        assertTrue(log.contains("[profile] compiled=1001"));
        var process = new ProcessBuilder(result.artifact().toString()).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        if (!toolchain.isMsvc()) {
            log.clear(); assertTrue(BuildExecutor.build(request).success());
            assertTrue(log.contains("[profile] local_reused=1001"));
            assertFalse(log.stream().anyMatch(line -> line.startsWith("[profile] compiled=")));
        }
        try (var files = Files.walk(root.resolve("build"))) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".rsp"))); }
    }
}
