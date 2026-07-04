package dtm.builder.placeholder;

import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderResolverTest {

    private static final String JSON = """
            {
              "id": "MyProjectTeste",
              "name": "MyProjectTeste",
              "version": "1.0.0",
              "properties": { "greeting": "ola" },
              "env": { "MYVAR": "xyz" },
              "activeProfile": "dev",
              "profiles": {
                "dev": { "buildType": "Debug", "properties": { "flavor": "development" } }
              }
            }
            """;

    private PlaceholderResolver resolver() {
        ManifestRootModel raw = ManifestParser.readManifest(JSON, false).getManifest();
        ManifestRootModel eff = ManifestProfiles.effective(raw);
        return new PlaceholderResolver(new PlaceholderContext(Path.of("."), eff));
    }

    @Test
    void resolvesProjectAndUserPlaceholders() {
        PlaceholderResolver r = resolver();
        assertEquals("1.0.0", r.resolve("${project.version}"));
        assertEquals("MyProjectTeste", r.resolve("${project.name}"));
        assertEquals(System.getProperty("user.home"), r.resolve("${user.home}"));
    }

    @Test
    void resolvesProfileAndProperties() {
        PlaceholderResolver r = resolver();
        assertEquals("development", r.resolve("${profile.current.flavor}"));
        assertEquals("Debug", r.resolve("${profile.dev.buildType}"));
        assertEquals("ola", r.resolve("${properties.greeting}"));
        assertEquals("ola", r.resolve("${greeting}"));
    }

    @Test
    void resolvesEnvAndMixedString() {
        PlaceholderResolver r = resolver();
        assertEquals("xyz", r.resolve("${env.MYVAR}"));
        assertEquals("v=1.0.0/development", r.resolve("v=${project.version}/${profile.current.flavor}"));
    }

    @Test
    void keepsUnresolvedVerbatim() {
        PlaceholderResolver r = resolver();
        assertEquals("${nope.nope}", r.resolve("${nope.nope}"));
    }
}
