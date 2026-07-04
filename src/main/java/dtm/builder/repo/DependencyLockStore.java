package dtm.builder.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dtm.builder.manifest.model.DependencyLock;
import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ResolvedDependency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DependencyLockStore {

    private static final ObjectMapper MAPPER = RepoJson.MAPPER;

    private DependencyLockStore() {
    }

    public static Path path(Path projectPath) {
        return projectPath.resolve(DependencyLock.FILE_NAME);
    }

    public static DependencyLock read(Path projectPath) throws IOException {
        Path file = path(projectPath);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        DependencyLock lock = MAPPER.readValue(file.toFile(), DependencyLock.class);
        if (lock == null || lock.getSchemaVersion() != DependencyLock.SCHEMA_VERSION) {
            throw new IOException("Versao de schema do lock nao suportada");
        }
        return lock;
    }

    public static boolean write(Path projectPath, DependencyLock lock) throws IOException {
        Path file = path(projectPath);
        byte[] content = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(lock);
        if (Files.isRegularFile(file) && java.util.Arrays.equals(Files.readAllBytes(file), content)) {
            return false;
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, content);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    public static boolean delete(Path projectPath) throws IOException {
        return Files.deleteIfExists(path(projectPath));
    }

    public static String fingerprint(List<ManifestPackagesModel> packages) {
        StringBuilder canonical = new StringBuilder();
        for (ManifestPackagesModel pkg : packages) {
            append(canonical, pkg == null ? null : pkg.getId());
            append(canonical, pkg == null ? null : pkg.getVersion());
            append(canonical, pkg == null ? null : pkg.getVersionConstraint());
            append(canonical, pkg == null ? null : pkg.getDownloadUrl());
            append(canonical, pkg == null ? null : Boolean.toString(pkg.isTransitive()));
        }
        return sha256(canonical.toString());
    }

    public static DependencyLock fromResolved(List<ManifestPackagesModel> declared,
                                              List<ResolvedDependency> resolved) {
        DependencyLock lock = new DependencyLock();
        lock.setPackagesFingerprint(fingerprint(declared));
        List<DependencyLock.LockedDependency> packages = new ArrayList<>();
        Set<String> selectedIds = resolved.stream().map(ResolvedDependency::id)
                .collect(Collectors.toSet());
        for (ResolvedDependency dependency : resolved) {
            DependencyLock.LockedDependency item = new DependencyLock.LockedDependency();
            item.setId(dependency.id());
            item.setVersion(dependency.declared().getVersion());
            item.setVariant(dependency.platformToken());
            item.setDownloadUrl(dependency.declared().getDownloadUrl());
            List<String> children = dependency.libraryManifest().getDependencies().stream()
                    .map(ManifestPackagesModel::getId)
                    .filter(id -> id != null && !id.isBlank() && selectedIds.contains(id))
                    .sorted()
                    .toList();
            item.setDependencies(children);
            packages.add(item);
        }
        packages.sort(Comparator.comparing(DependencyLock.LockedDependency::getId)
                .thenComparing(DependencyLock.LockedDependency::getVersion));
        lock.setPackages(packages);
        return lock;
    }

    private static void append(StringBuilder target, String value) {
        String normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append(';');
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
