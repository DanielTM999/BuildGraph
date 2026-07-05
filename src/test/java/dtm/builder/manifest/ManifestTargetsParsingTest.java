package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestParseResult;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTargetsParsingTest {

    private static final String JSON = """
            {
              "id": "vema",
              "version": "0.1.0",
              "sources": ["src/shared"],
              "targets": [
                { "id": "vema-core", "type": "shared", "sources": ["src/core"] },
                { "id": "vema-jit", "type": "static", "sources": ["src/jit"], "dependsOn": ["vema-core"] },
                { "id": "vema", "sources": ["src/runtime"], "dependsOn": ["vema-core", "vema-jit"] }
              ],
              "profiles": {
                "windows": {
                  "targets": [ { "id": "vema", "defines": ["VEMA_WIN32"] } ]
                }
              }
            }
            """;

    @Test
    void parsesTargetsFromJson() {
        ManifestParseResult result = ManifestParser.readManifest(JSON, false);
        assertTrue(result.isOk());
        ManifestRootModel m = result.getManifest();
        assertEquals(3, m.getTargets().size());

        ManifestTargetModel core = m.getTargets().get(0);
        assertEquals("vema-core", core.getId());
        assertEquals("shared", core.getType());
        assertEquals(List.of("src/core"), core.getSources());

        ManifestTargetModel jit = m.getTargets().get(1);
        assertEquals("static", jit.getType());
        assertEquals(List.of("vema-core"), jit.getDependsOn());

        ManifestTargetModel exe = m.getTargets().get(2);
        assertEquals(List.of("vema-core", "vema-jit"), exe.getDependsOn());

        List<ManifestTargetModel> profileTargets = m.getProfiles().get("windows").getTargets();
        assertEquals(1, profileTargets.size());
        assertEquals("vema", profileTargets.get(0).getId());
        assertEquals(List.of("VEMA_WIN32"), profileTargets.get(0).getDefines());
    }

    @Test
    void parsesTargetsFromXml() {
        String xml = """
                <Manifest>
                  <id>vema</id>
                  <version>0.1.0</version>
                  <targets>
                    <target>
                      <id>vema-core</id>
                      <type>shared</type>
                      <sources>
                        <source>src/core</source>
                      </sources>
                    </target>
                    <target>
                      <id>vema</id>
                      <sources>
                        <source>src/runtime</source>
                      </sources>
                      <dependsOn>
                        <dependency>vema-core</dependency>
                      </dependsOn>
                    </target>
                  </targets>
                </Manifest>
                """;
        ManifestParseResult result = ManifestParser.readManifest(xml, true);
        assertTrue(result.isOk());
        ManifestRootModel m = result.getManifest();
        assertEquals(2, m.getTargets().size());
        assertEquals("vema-core", m.getTargets().get(0).getId());
        assertEquals("shared", m.getTargets().get(0).getType());
        assertEquals(List.of("vema-core"), m.getTargets().get(1).getDependsOn());
    }

    @Test
    void reportsTargetWithoutIdAsError() {
        ManifestParseResult result = ManifestParser.readManifest(
                "{ \"id\": \"x\", \"version\": \"1\", \"targets\": [ { \"type\": \"shared\" } ] }", false);
        assertFalse(result.isOk());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.target-id-missing".equals(d.getCode())));
    }

    @Test
    void reportsDuplicateTargetIdsAsError() {
        ManifestParseResult result = ManifestParser.readManifest("""
                { "id": "x", "version": "1",
                  "targets": [ { "id": "a" }, { "id": "a" } ] }
                """, false);
        assertFalse(result.isOk());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.target-id-duplicate".equals(d.getCode())));
    }

    @Test
    void reportsUnknownTargetTypeAsError() {
        ManifestParseResult result = ManifestParser.readManifest("""
                { "id": "x", "version": "1",
                  "targets": [ { "id": "a", "type": "plugin" } ] }
                """, false);
        assertFalse(result.isOk());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.target-type-unknown".equals(d.getCode())));
    }

    @Test
    void reportsSelfDependencyAsError() {
        ManifestParseResult result = ManifestParser.readManifest("""
                { "id": "x", "version": "1",
                  "targets": [ { "id": "a", "dependsOn": ["a"] } ] }
                """, false);
        assertFalse(result.isOk());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.target-self-dependency".equals(d.getCode())));
    }

    @Test
    void warnsWhenLibraryAndTargetsCoexist() {
        ManifestParseResult result = ManifestParser.readManifest("""
                { "id": "x", "version": "1", "library": true,
                  "targets": [ { "id": "a" } ] }
                """, false);
        assertTrue(result.isOk(), "library+targets is only a warning");
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(d -> "manifest.library-with-targets".equals(d.getCode())));
    }
}
