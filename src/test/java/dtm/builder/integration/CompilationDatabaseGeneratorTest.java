package dtm.builder.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dtm.builder.build.Toolchain;
import dtm.builder.build.ToolchainKind;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilationDatabaseGeneratorTest {

    @TempDir
    Path project;

    @Test
    void generatesOneCompileOnlyEntryPerSource() throws Exception {
        Path src = Files.createDirectories(project.resolve("src"));
        Path include = Files.createDirectories(project.resolve("include"));
        Files.writeString(src.resolve("main.c"), "int main(void) { return 0; }");
        Files.writeString(src.resolve("feature.cpp"), "int feature() { return 1; }");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setSources(new ArrayList<>(List.of("src")));
        manifest.setIncludes(new ArrayList<>(List.of("include")));
        manifest.setDefines(new ArrayList<>(List.of("APP=1")));
        manifest.setCompileFlags(new ArrayList<>(List.of("-Wall")));
        manifest.setLinkFlags(new ArrayList<>(List.of("-Wl,should-not-appear")));
        manifest.setCStandard("c17");
        manifest.setCxxStandard("c++20");
        Toolchain toolchain = new Toolchain(ToolchainKind.CUSTOM,
                Path.of("tool-gcc"), Path.of("tool-g++"));

        String json = CompilationDatabaseGenerator.generate(project, manifest, toolchain,
                project.resolve("packages"), project.resolve("build"), "Release");
        JsonNode entries = JsonMapper.builder().build().readTree(json);

        assertEquals(2, entries.size());
        JsonNode c = entryFor(entries, "main.c");
        JsonNode cpp = entryFor(entries, "feature.cpp");
        List<String> cArgs = arguments(c);
        List<String> cppArgs = arguments(cpp);
        assertEquals(project.toAbsolutePath().normalize().toString(), c.get("directory").asText());
        assertEquals("tool-gcc", cArgs.get(0));
        assertEquals("tool-g++", cppArgs.get(0));
        assertTrue(cArgs.contains("-std=c17"));
        assertTrue(cppArgs.contains("-std=c++20"));
        assertTrue(cArgs.contains("-I" + include.toAbsolutePath().normalize()));
        assertTrue(cArgs.contains("-DAPP=1"));
        assertTrue(cArgs.contains("-Wall"));
        assertTrue(cArgs.contains("-O2"));
        assertTrue(cArgs.contains("-c"));
        assertTrue(cArgs.contains("-o"));
        assertFalse(cArgs.contains("-Wl,should-not-appear"));
        assertTrue(c.has("output"));
    }

    @Test
    void emptyProjectProducesEmptyDatabaseWithoutToolchain() {
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setSources(new ArrayList<>(List.of("missing")));

        String json = CompilationDatabaseGenerator.generate(project, manifest, null,
                project.resolve("packages"), project.resolve("build"), "Debug");

        assertEquals("[]", json);
    }

    @Test
    void targetEntryIncludesTransitiveDependencyIncludes() throws Exception {
        Files.createDirectories(project.resolve("src/base"));
        Files.createDirectories(project.resolve("src/core"));
        Files.createDirectories(project.resolve("src/app"));
        Path baseInclude = Files.createDirectories(project.resolve("include/base"));
        Path coreInclude = Files.createDirectories(project.resolve("include/core"));
        Files.writeString(project.resolve("src/base/base.c"), "int base(void) { return 1; }");
        Files.writeString(project.resolve("src/core/core.c"), "int core(void) { return 2; }");
        Files.writeString(project.resolve("src/app/main.c"), "int main(void) { return 0; }");

        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setTargets(new ArrayList<>(List.of(
                target("base", "static", List.of("src/base"), List.of("include/base"),
                        List.of()),
                target("core", "static", List.of("src/core"), List.of("include/core"),
                        List.of("base")),
                target("app", "executable", List.of("src/app"), List.of(),
                        List.of("core")))));
        Toolchain toolchain = new Toolchain(ToolchainKind.CUSTOM,
                Path.of("tool-gcc"), Path.of("tool-g++"));

        String json = CompilationDatabaseGenerator.generate(project, manifest, toolchain,
                project.resolve("packages"), project.resolve("build"), "Debug");
        JsonNode entries = JsonMapper.builder().build().readTree(json);
        List<String> appArgs = arguments(entryFor(entries, "main.c"));

        assertTrue(appArgs.contains("-I" + coreInclude.toAbsolutePath().normalize()));
        assertTrue(appArgs.contains("-I" + baseInclude.toAbsolutePath().normalize()));
    }

    private static JsonNode entryFor(JsonNode entries, String fileName) {
        for (JsonNode entry : entries) {
            if (entry.get("file").asText().endsWith(fileName)) {
                return entry;
            }
        }
        throw new AssertionError("Entry not found: " + fileName);
    }

    private static List<String> arguments(JsonNode entry) {
        List<String> result = new ArrayList<>();
        entry.get("arguments").forEach(value -> result.add(value.asText()));
        return result;
    }

    private static ManifestTargetModel target(String id, String type, List<String> sources,
                                              List<String> includes, List<String> dependsOn) {
        ManifestTargetModel target = new ManifestTargetModel();
        target.setId(id);
        target.setType(type);
        target.setSources(new ArrayList<>(sources));
        target.setIncludes(new ArrayList<>(includes));
        target.setDependsOn(new ArrayList<>(dependsOn));
        return target;
    }
}
