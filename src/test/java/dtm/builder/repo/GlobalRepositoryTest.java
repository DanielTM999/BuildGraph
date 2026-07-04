package dtm.builder.repo;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.ManifestPackagesModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalRepositoryTest {

    @TempDir
    Path temp;

    @Test
    void resolvesFromRepositoriesInOrder() throws Exception {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        createPackage(second, "fmt", "1", "second");
        GlobalRepository repository = GlobalRepository.at(List.of(first, second));

        GlobalRepository.VariantResolution resolution = repository.resolveVariant(
                packageModel("fmt", "1"), null);

        assertTrue(resolution.found());
        assertTrue(resolution.dir().startsWith(second));
    }

    @Test
    void publishesIntoFirstRepository() throws Exception {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        Path content = temp.resolve("content");
        Files.createDirectories(content);
        Files.writeString(content.resolve("header.h"), "#pragma once");
        LibraryManifest manifest = new LibraryManifest();
        manifest.setId("mine");
        manifest.setVersion("1");
        GlobalRepository repository = GlobalRepository.at(List.of(first, second));

        Path published = repository.publish("mine", "1", content, manifest);

        assertTrue(published.startsWith(first));
        assertTrue(Files.isRegularFile(published.resolve("Manifest.json")));
    }

    @Test
    void resolvesManifestPathsBeforeCliAndDefaultAndDeduplicates() {
        Path project = temp.resolve("project");
        Path absolute = temp.resolve("absolute").toAbsolutePath().normalize();

        GlobalRepository repository = GlobalRepository.resolve(project,
                List.of("repo", absolute.toString(), "repo"), temp.resolve("cli").toString());

        assertEquals(project.resolve("repo").toAbsolutePath().normalize(), repository.roots().get(0));
        assertEquals(absolute, repository.roots().get(1));
        assertEquals(temp.resolve("cli").toAbsolutePath().normalize(), repository.roots().get(2));
        assertEquals(4, repository.roots().size());
    }

    private static ManifestPackagesModel packageModel(String id, String version) {
        ManifestPackagesModel model = new ManifestPackagesModel();
        model.setId(id);
        model.setVersion(version);
        return model;
    }

    private static void createPackage(Path root, String id, String version, String marker)
            throws Exception {
        Path variant = root.resolve(id).resolve(version).resolve("source");
        Files.createDirectories(variant.resolve("lib"));
        Files.writeString(variant.resolve("lib").resolve(marker), marker);
        Files.writeString(variant.resolve("Manifest.json"), "{}");
    }
}
