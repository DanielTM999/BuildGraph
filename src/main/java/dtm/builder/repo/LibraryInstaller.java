package dtm.builder.repo;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.ManifestPackagesModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class LibraryInstaller {

    private final GlobalRepository repository;
    private final LibraryDownloader downloader;

    public LibraryInstaller(GlobalRepository repository, LibraryDownloader downloader) {
        this.repository = repository;
        this.downloader = downloader;
    }

    public void ensureInstalled(ManifestPackagesModel declared) throws IOException {
        validate(declared);
        if (repository.isInstalled(declared)) {
            return;
        }
        if (!declared.hasDownloadUrl()) {
            throw new IOException("Package '" + declared.key()
                    + "' nao esta no repo global e nao possui downloadUrl.");
        }
        installFromUrl(declared);
    }

    private void installFromUrl(ManifestPackagesModel declared) throws IOException {
        Path staging = repository.stagingDir().resolve(
                PathSanitizer.sanitizeId(declared.getId()) + "-"
                        + PathSanitizer.sanitizeVersion(declared.getVersion()) + "-"
                        + UUID.randomUUID());
        Path extracted = staging.resolve("extracted");
        Files.createDirectories(extracted);
        try {
            byte[] zip = downloader.downloadZip(declared.getDownloadUrl());
            try (ByteArrayInputStream in = new ByteArrayInputStream(zip)) {
                SafeZipExtractor.extract(in, extracted);
            }
            Path packageRoot = SafeZipExtractor.findPackageRoot(extracted);
            LibraryManifest manifest = resolveOrNormalizeManifest(packageRoot, declared);
            repository.storeBundle(packageRoot, manifest, declared);
        } finally {
            SafeZipExtractor.deleteTree(staging);
        }
    }

    private LibraryManifest resolveOrNormalizeManifest(Path packageRoot,
                                                       ManifestPackagesModel declared) throws IOException {
        Path candidate = packageRoot.resolve(GlobalRepository.GLOBAL_MANIFEST_FILE);
        LibraryManifest manifest;
        if (Files.isRegularFile(candidate)) {
            manifest = RepoJson.read(candidate, LibraryManifest.class);
        } else {
            manifest = new LibraryManifest();
        }

        if (isBlank(manifest.getId())) {
            manifest.setId(declared.getId());
        }
        if (isBlank(manifest.getVersion())) {
            manifest.setVersion(declared.getVersion());
        }
        if (isBlank(manifest.getName())) {
            manifest.setName(declared.getId());
        }
        if (isBlank(manifest.getKind())) {
            manifest.setKind(LibraryManifest.KIND_SOURCE);
        }
        if (manifest.getIncludes().isEmpty()) {
            manifest.getIncludes().add(Files.isDirectory(packageRoot.resolve("include"))
                    ? "include" : ".");
        }
        if (manifest.getSources().isEmpty()
                && Files.isDirectory(packageRoot.resolve("src"))) {
            manifest.getSources().add("src");
        }
        manifest.setSource("Manifest");
        return manifest;
    }

    private void validate(ManifestPackagesModel declared) throws IOException {
        if (declared == null) {
            throw new IOException("Package nulo");
        }
        if (isBlank(declared.getId())) {
            throw new IOException("Package sem id");
        }
        if (isBlank(declared.getVersion())) {
            throw new IOException("Package '" + declared.getId() + "' sem version");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
