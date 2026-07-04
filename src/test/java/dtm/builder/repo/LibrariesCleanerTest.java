package dtm.builder.repo;

import dtm.builder.manifest.model.PackageLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibrariesCleanerTest {

    @TempDir
    Path packagesDir;

    @Test
    void removesManagedOrphanButKeepsForeignAndKept() throws Exception {
        managed("libfoo");           // gerenciado, sera removido (nao esta no keep)
        managed("libbar");           // gerenciado, mas esta no keep -> preservado
        Path foreign = packagesDir.resolve("nao-gerenciado");
        Files.createDirectories(foreign);
        Files.writeString(foreign.resolve("data.txt"), "keep me");
        Path tmp = packagesDir.resolve(".tmp-libfoo-abc");
        Files.createDirectories(tmp);

        boolean changed = LibrariesCleaner.clean(packagesDir, Set.of("libbar"));

        assertTrue(changed);
        assertFalse(Files.exists(packagesDir.resolve("libfoo")), "orfa gerenciada deve sumir");
        assertFalse(Files.exists(tmp), "temporario deve sumir");
        assertTrue(Files.exists(packagesDir.resolve("libbar")), "dep em keep deve ficar");
        assertTrue(Files.exists(foreign), "pasta alheia nunca deve ser removida");
    }

    @Test
    void emptyKeepDematerializesOnlyManagedFolders() throws Exception {
        managed("libfoo");
        Path foreign = packagesDir.resolve("meus-arquivos");
        Files.createDirectories(foreign);

        boolean changed = LibrariesCleaner.clean(packagesDir, Set.of());

        assertTrue(changed);
        assertFalse(Files.exists(packagesDir.resolve("libfoo")));
        assertTrue(Files.exists(foreign));
    }

    private void managed(String name) throws IOException {
        Path dir = packagesDir.resolve(name);
        Files.createDirectories(dir);
        PackageLock lock = new PackageLock();
        lock.setId(name);
        lock.setVersion("1.0.0");
        lock.setManagedBy(PackageLock.MANAGED_BY);
        RepoJson.write(dir.resolve(PackageLock.FILE_NAME), lock);
    }
}
