package dtm.builder.manifest;

import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.repo.GlobalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManifestResolverPrecedenceTest {

    @TempDir
    Path project;

    @Test
    void manifestActiveProfileWinsOverCliProfile() throws Exception {
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "activeProfile":"manifest",
                  "profiles":{
                    "manifest":{"platform":"from-manifest"},
                    "cli":{"platform":"from-cli"}
                  }
                }
                """);
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(project.resolve("repo")));

        ManifestRootModel effective = resolver.readEffective("cli");

        assertEquals("from-manifest", effective.getPlatform());
    }

    @Test
    void cliProfileIsFallbackWhenManifestDoesNotSelectOne() throws Exception {
        Files.writeString(project.resolve("Manifest.json"), """
                {"profiles":{"cli":{"platform":"from-cli"}}}
                """);
        ManifestResolver resolver = new ManifestResolver(project,
                GlobalRepository.at(project.resolve("repo")));

        ManifestRootModel effective = resolver.readEffective("cli");

        assertEquals("from-cli", effective.getPlatform());
    }
}
