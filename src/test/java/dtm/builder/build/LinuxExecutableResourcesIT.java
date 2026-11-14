package dtm.builder.build;

import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LinuxExecutableResourcesIT {
    @TempDir Path project;

    @Test void embedsElfSectionsAndPreservesThemAfterStrip() throws Exception {
        assumeTrue(TargetPlatform.host().os().equals("linux") && ToolProbe.exists("gcc")
                && ToolProbe.exists("readelf") && ToolProbe.exists("strip") && ToolProbe.exists("objcopy"));
        Files.writeString(project.resolve("main.c"), "int main(void) { return 0; }\n");
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        ImageIO.write(image, "png", project.resolve("icon.png").toFile());
        String base = """
                {"name":"elf-resource-test", "version":"1.0.0", "sources":["main.c"],
                 "cCompiler":"gcc", "icon":"icon.png",
                 "metadata":{"productName":"Máquina Virtual", "customKey":"customValue"}}
                """;
        List<String> log = new ArrayList<>();
        var manifest = ManifestProfiles.effective(ManifestParser.readManifest(base, false).getManifest());
        BuildRequest req = new BuildRequest(project, manifest, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", log::add);
        BuildResult result = BuildExecutor.build(req);
        assertTrue(result.success(), result.message() + log);
        Path exe = result.artifact();
        assertSections(exe);
        Path dumped = project.resolve("icon-dumped.png");
        assertEquals(0, ProcessExecutor.REAL.run(List.of("objcopy", "--dump-section",
                ".buildgraph.icon=" + dumped, exe.toString()), project, Map.of(), log::add));
        assertArrayEquals(Files.readAllBytes(project.resolve("icon.png")), Files.readAllBytes(dumped));
        Path metadata = project.resolve("metadata-dumped.json");
        assertEquals(0, ProcessExecutor.REAL.run(List.of("objcopy", "--dump-section",
                ".buildgraph.metadata=" + metadata, exe.toString()), project, Map.of(), log::add));
        String json = Files.readString(metadata);
        assertTrue(json.contains("\"customKey\":\"customValue\""), json);
        assertTrue(json.contains("Máquina Virtual"), json);
        Path stripped = project.resolve("stripped");
        Files.copy(exe, stripped);
        assertEquals(0, ProcessExecutor.REAL.run(List.of("strip", stripped.toString()), project, Map.of(), log::add));
        assertSections(stripped);

        log.clear();
        assertTrue(BuildExecutor.build(req).success());
        assertFalse(log.stream().anyMatch(s -> s.contains("resources.s") && s.contains(" -c ")), log.toString());
        image.setRGB(1, 0, 0xff00ff00);
        ImageIO.write(image, "png", project.resolve("icon.png").toFile());
        log.clear();
        assertTrue(BuildExecutor.build(req).success());
        assertTrue(log.stream().anyMatch(s -> s.contains("resources.s") && s.contains(" -c ")), log.toString());
        assertFalse(log.stream().anyMatch(s -> s.contains("main.c") && s.contains(" -c ")), log.toString());

        var changedMetadata = ManifestProfiles.effective(ManifestParser.readManifest(
                base.replace("customValue", "updatedValue"), false).getManifest());
        log.clear();
        assertTrue(BuildExecutor.build(new BuildRequest(project, changedMetadata, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", log::add)).success());
        assertTrue(log.stream().anyMatch(s -> s.contains("resources.s") && s.contains(" -c ")), log.toString());
        assertFalse(log.stream().anyMatch(s -> s.contains("main.c") && s.contains(" -c ")), log.toString());

        var removed = ManifestProfiles.effective(ManifestParser.readManifest("""
                {"name":"elf-resource-test","version":"1.0.0","sources":["main.c"],"cCompiler":"gcc"}
                """, false).getManifest());
        result = BuildExecutor.build(new BuildRequest(project, removed, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", log::add));
        assertTrue(result.success(), result.message() + log);
        List<String> sections = new ArrayList<>();
        assertEquals(0, ProcessExecutor.REAL.run(List.of("readelf", "-W", "-S", result.artifact().toString()),
                project, Map.of(), sections::add));
        assertFalse(sections.stream().anyMatch(s -> s.contains(".buildgraph.icon") || s.contains(".buildgraph.metadata")));
    }

    private void assertSections(Path exe) {
        List<String> sections = new ArrayList<>();
        assertEquals(0, ProcessExecutor.REAL.run(List.of("readelf", "-W", "-S", exe.toString()),
                project, Map.of(), sections::add));
        assertTrue(sections.stream().anyMatch(s -> s.contains(".buildgraph.icon")), sections.toString());
        assertTrue(sections.stream().anyMatch(s -> s.contains(".buildgraph.metadata")), sections.toString());
    }
}
