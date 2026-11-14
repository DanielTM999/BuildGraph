package dtm.builder.build;

import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsExecutableResourcesIT {
    @TempDir Path project;

    @Test void embedsResourcesAndRelinksOnlyWhenResourcesChange() throws Exception {
        assumeTrue(TargetPlatform.host().windows() && ToolProbe.exists("gcc") && ToolProbe.exists("windres"));
        project = Files.createDirectories(project.resolve("project with spaces"));
        Files.writeString(project.resolve("main.c"), "int main(void) { return 0; }\n");
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        ImageIO.write(image, "png", project.resolve("ícone.png").toFile());
        String base = """
                {"name":"resource-test","version":"1.0.0-beta","sources":["main.c"],
                 "cCompiler":"gcc", "icon":"ícone.png",
                 "metadata":{"productName":"Máquina Virtual","companyName":"Vertex"}}
                """;
        var manifest = ManifestProfiles.effective(ManifestParser.readManifest(base, false).getManifest());
        List<String> logs = new ArrayList<>();
        BuildRequest req = new BuildRequest(project, manifest, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", logs::add);
        BuildResult first = BuildExecutor.build(req);
        assertTrue(first.success(), first.message() + logs);
        Path exe = first.artifact();
        assertTrue(Files.isRegularFile(exe));
        assertTrue(logs.stream().anyMatch(s -> s.contains("windres")));
        if (ToolProbe.exists("objdump")) {
            List<String> sections = new ArrayList<>();
            assertEquals(0, ProcessExecutor.REAL.run(List.of("objdump", "-h", exe.toString()),
                    project, java.util.Map.of(), sections::add));
            assertTrue(sections.stream().anyMatch(s -> s.contains(".rsrc")), sections.toString());
        }
        logs.clear();
        assertTrue(BuildExecutor.build(req).success());
        assertFalse(logs.stream().anyMatch(s -> s.contains("windres")), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("gcc") && s.contains("-o") && s.contains(".exe")), logs.toString());
        image.setRGB(1, 0, 0xff00ff00);
        ImageIO.write(image, "png", project.resolve("ícone.png").toFile());
        logs.clear();
        assertTrue(BuildExecutor.build(req).success());
        assertTrue(logs.stream().anyMatch(s -> s.contains("windres")), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("main.c") && s.contains(" -c ")), logs.toString());
        var updated = ManifestProfiles.effective(ManifestParser.readManifest(
                base.replace("Máquina Virtual", "Máquina Debug"), false).getManifest());
        logs.clear();
        assertTrue(BuildExecutor.build(new BuildRequest(project, updated, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", logs::add)).success());
        assertTrue(logs.stream().anyMatch(s -> s.contains("windres")), logs.toString());
        assertFalse(logs.stream().anyMatch(s -> s.contains("main.c") && s.contains(" -c ")), logs.toString());
        Files.writeString(project.resolve("main.c"), "int main(void) { return 1; }\n");
        logs.clear();
        assertTrue(BuildExecutor.build(new BuildRequest(project, updated, null, BuildSystem.MANIFEST,
                project.resolve("packages"), project.resolve("build"), "Debug", logs::add)).success());
        assertFalse(logs.stream().anyMatch(s -> s.contains("windres")), logs.toString());
        var removed = ManifestParser.readManifest("""
                {"name":"resource-test","version":"1.0.0-beta","sources":["main.c"],"cCompiler":"gcc"}
                """, false).getManifest();
        logs.clear();
        BuildResult noResources = BuildExecutor.build(new BuildRequest(project, ManifestProfiles.effective(removed),
                null, BuildSystem.MANIFEST, project.resolve("packages"), project.resolve("build"), "Debug", logs::add));
        assertTrue(noResources.success(), noResources.message() + logs);
        assertTrue(logs.stream().anyMatch(s -> s.contains(".exe") && s.contains("gcc")), logs.toString());
    }
}
