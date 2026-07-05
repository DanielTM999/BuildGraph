package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestProfilesTest {

    private static final String JSON = """
            {
              "compilerVersion": "c++17",
              "platform": "x86_64-pc-windows-msvc",
              "toolchainVersion": "17.0.0",
              "includes": ["include"],
              "defines": ["APP=1"],
              "properties": { "base": "root" },
              "repositories": ["repo/base", "repo/shared"],
              "packages": [ { "id": "a", "version": "1" } ],
              "activeProfile": "arm",
              "profiles": {
                "arm": {
                  "platform": "linux-arm64",
                  "compilerVersion": "c++20",
                  "includes": ["extra/inc"],
                  "excludeIncludes": ["include"],
                  "defines": ["ARM=1"],
                  "properties": { "flavor": "embedded" },
                  "repositories": ["repo/shared", "repo/arm"],
                  "packages": [ { "id": "b", "version": "2" } ]
                }
              }
            }
            """;

    @Test
    void mergesActiveProfile() {
        ManifestRootModel raw = ManifestParser.readManifest(JSON, false).getManifest();
        ManifestRootModel eff = ManifestProfiles.effective(raw);

        assertEquals("linux-arm64", eff.getPlatform());
        assertEquals("c++20", eff.getCompilerVersion());
        assertEquals("17.0.0", eff.getToolchainVersion());

        assertEquals(java.util.List.of("extra/inc"), eff.getIncludes());

        assertTrue(eff.getDefines().contains("APP=1"));
        assertTrue(eff.getDefines().contains("ARM=1"));

        assertEquals("root", eff.getProperties().get("base"));
        assertEquals("embedded", eff.getProperties().get("flavor"));

        assertEquals(java.util.List.of("repo/base", "repo/shared", "repo/arm"),
                eff.getRepositories());

        assertEquals(2, eff.getPackages().size());
        assertTrue(eff.isPackagesDeclared());
    }

    @Test
    void profilePackageOverridesRootPackageWithSameId() {
        ManifestRootModel raw = ManifestParser.readManifest("""
                {
                  "packages": [{"id":"a","version":"1"}],
                  "activeProfile":"win",
                  "profiles":{"win":{"packages":[{"id":"a","version":"2"}]}}
                }
                """, false).getManifest();

        ManifestRootModel eff = ManifestProfiles.effective(raw);

        assertEquals(1, eff.getPackages().size());
        assertEquals("2", eff.getPackages().get(0).getVersion());
    }

    @Test
    void noProfileReturnsRootValues() {
        ManifestRootModel raw = ManifestParser.readManifest(
                "{ \"platform\": \"host\", \"includes\": [\"inc\"] }", false).getManifest();
        ManifestRootModel eff = ManifestProfiles.effective(raw);
        assertEquals("host", eff.getPlatform());
        assertEquals(java.util.List.of("inc"), eff.getIncludes());
    }
}
