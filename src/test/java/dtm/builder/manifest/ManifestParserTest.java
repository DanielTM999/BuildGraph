package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestParseResult;
import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestParserTest {

    @Test
    void parsesRepositoriesFromJsonAndXml() {
        ManifestRootModel json = ManifestParser.readManifest(
                "{\"repositories\":[\"repo/a\",\"D:/repo-b\"]}", false).getManifest();
        ManifestRootModel xml = ManifestParser.readManifest("""
                <Manifest>
                  <repositories>repo/a</repositories>
                  <repositories>D:/repo-b</repositories>
                </Manifest>
                """, true).getManifest();

        assertEquals(java.util.List.of("repo/a", "D:/repo-b"), json.getRepositories());
        assertEquals(java.util.List.of("repo/a", "D:/repo-b"), xml.getRepositories());
    }

    private static final String JSON = """
            {
              "id": "MyProjectTeste",
              "name": "MyProjectTeste",
              "version": "1.0.0",
              "cxxStandard": "cpp20",
              "sourceFolders": ["src"],
              "testFolder": "specs",
              "testMain": "TestMain.cpp",
              "includePaths": ["include"],
              "defines": ["APP=1"],
              "packages": [ { "id": "fmt", "version": "10.2.1" } ],
              "activeProfile": "dev",
              "profiles": {
                "dev": { "buildType": "Debug", "defines": ["DEV=1"] }
              },
              "tasks": [ { "id": "t1", "phase": "build", "command": "echo", "args": ["hi"] } ]
            }
            """;

    @Test
    void parsesJsonManifest() {
        ManifestParseResult result = ManifestParser.readManifest(JSON, false);
        assertTrue(result.isOk(), "manifest should be valid");
        ManifestRootModel m = result.getManifest();
        assertEquals("MyProjectTeste", m.getId());
        assertEquals("1.0.0", m.getVersion());
        assertEquals(java.util.List.of("src"), m.getSourceFolders());
        assertEquals("specs", m.getTestFolder());
        assertEquals("TestMain.cpp", m.getTestMain());
        assertEquals(1, m.getPackages().size());
        assertEquals("fmt", m.getPackages().get(0).getId());
        assertTrue(m.getProfiles().containsKey("dev"));
        assertEquals(1, m.getTasks().size());
        assertTrue(m.isPackagesDeclared());
    }

    @Test
    void parsesEquivalentXmlManifest() {
        String xml = """
                <Manifest>
                  <id>MyProjectTeste</id>
                  <name>MyProjectTeste</name>
                  <version>2.0.0</version>
                  <sourceFolders>src</sourceFolders>
                  <sourceFolders>lib</sourceFolders>
                  <packages>
                    <id>fmt</id>
                    <version>10.2.1</version>
                  </packages>
                </Manifest>
                """;
        ManifestParseResult result = ManifestParser.readManifest(xml, true);
        assertTrue(result.isOk());
        ManifestRootModel m = result.getManifest();
        assertEquals("MyProjectTeste", m.getId());
        assertEquals("2.0.0", m.getVersion());
        assertEquals(java.util.List.of("src", "lib"), m.getSourceFolders());
        assertEquals(1, m.getPackages().size());
        assertEquals("fmt", m.getPackages().get(0).getId());
    }

    @Test
    void reportsMissingVersionAsWarning() {
        ManifestParseResult result = ManifestParser.readManifest(
                "{ \"id\": \"x\", \"name\": \"x\" }", false);
        assertTrue(result.isOk(), "missing version is only a warning");
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.version-missing".equals(d.getCode())));
    }

    @Test
    void reportsPackageMissingIdAsError() {
        ManifestParseResult result = ManifestParser.readManifest(
                "{ \"version\": \"1.0.0\", \"packages\": [ { \"version\": \"1\" } ] }", false);
        assertFalse(result.isOk());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.packages-id-missing".equals(d.getCode())));
    }
}
