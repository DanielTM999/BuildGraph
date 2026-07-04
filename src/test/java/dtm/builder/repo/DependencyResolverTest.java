package dtm.builder.repo;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ResolvedDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyResolverTest {

    @TempDir
    Path temp;

    @Test
    void choosesHighestCompatibleVersionAndPropagatesTransitiveDependencies() throws Exception {
        publish("core", "1.4.0", List.of());
        publish("core", "1.8.0", List.of());
        publish("core", "2.0.0", List.of());
        publish("wrapper", "1.0.0", List.of(constraint("core", "[1.0,2.0)")));

        List<ResolvedDependency> resolved = resolver().resolve(List.of(
                exact("wrapper", "1.0.0"), constraint("core", "<1.6")));

        assertEquals(List.of("wrapper:1.0.0", "core:1.4.0"),
                resolved.stream().map(ResolvedDependency::key).toList());
    }

    @Test
    void reportsConflictWithOrigins() throws Exception {
        publish("core", "1.5.0", List.of());
        publish("left", "1.0.0", List.of(constraint("core", "<2.0")));
        publish("right", "1.0.0", List.of(constraint("core", ">=2.0")));

        DependencyResolutionException error = assertThrows(DependencyResolutionException.class,
                () -> resolver().resolve(List.of(exact("left", "1.0.0"),
                        exact("right", "1.0.0"))));

        assertTrue(error.getMessage().contains("Conflito de versao para 'core'"));
        assertTrue(error.getMessage().contains("left"));
        assertTrue(error.getMessage().contains("right"));
    }

    @Test
    void transitiveFalseStopsTraversal() throws Exception {
        publish("child", "1.0.0", List.of());
        publish("parent", "1.0.0", List.of(exact("child", "1.0.0")));
        ManifestPackagesModel parent = exact("parent", "1.0.0");
        parent.setTransitive(false);

        List<ResolvedDependency> resolved = resolver().resolve(List.of(parent));

        assertEquals(List.of("parent:1.0.0"),
                resolved.stream().map(ResolvedDependency::key).toList());
    }

    private DependencyResolver resolver() {
        GlobalRepository repository = GlobalRepository.at(temp);
        return new DependencyResolver(repository,
                new LibraryInstaller(repository, new LibraryDownloader()));
    }

    private void publish(String id, String version, List<ManifestPackagesModel> dependencies)
            throws Exception {
        Path variant = temp.resolve(id).resolve(version).resolve("source");
        Files.createDirectories(variant.resolve("lib"));
        LibraryManifest manifest = new LibraryManifest();
        manifest.setId(id);
        manifest.setName(id);
        manifest.setVersion(version);
        manifest.setDependencies(dependencies);
        RepoJson.write(variant.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE), manifest);
    }

    private static ManifestPackagesModel exact(String id, String version) {
        ManifestPackagesModel model = new ManifestPackagesModel();
        model.setId(id);
        model.setVersion(version);
        return model;
    }

    private static ManifestPackagesModel constraint(String id, String constraint) {
        ManifestPackagesModel model = new ManifestPackagesModel();
        model.setId(id);
        model.setVersionConstraint(constraint);
        return model;
    }
}
