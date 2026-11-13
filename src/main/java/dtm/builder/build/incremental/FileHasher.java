package dtm.builder.build.incremental;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FileHasher {

    private record Snapshot(long size, FileTime modified,
                            FileTime created, Object fileKey, String hash) { }
    private final ConcurrentHashMap<Path, Snapshot> hashes = new ConcurrentHashMap<>();

    static final long RACY_WINDOW_MILLIS = 2000;
    static final String RACY = "racy";

    public String stamp(Path file) throws IOException {
        Path key = file.toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(key, BasicFileAttributes.class);
        long modified = attrs.lastModifiedTime().toMillis();
        String mtime = System.currentTimeMillis() - modified < RACY_WINDOW_MILLIS ? RACY : String.valueOf(modified);
        return attrs.size() + "|" + mtime + "|" + sha256(key, attrs);
    }

    public boolean matches(Path file, String recorded) {
        if (recorded == null || recorded.isBlank()) {
            return false;
        }
        String[] parts = recorded.split("\\|", 3);
        if (parts.length != 3) {
            return false;
        }
        Path key = file.toAbsolutePath().normalize();
        try {
            BasicFileAttributes attrs = Files.readAttributes(key, BasicFileAttributes.class);
            if (!String.valueOf(attrs.size()).equals(parts[0])) return false;
            return sha256(key, attrs).equals(parts[2]);
        } catch (IOException e) {
            return false;
        }
    }

    public String sha256(Path file) throws IOException {
        Path key = file.toAbsolutePath().normalize();
        return sha256(key, Files.readAttributes(key, BasicFileAttributes.class));
    }

    private String sha256(Path key, BasicFileAttributes attrs) throws IOException {

        try {
            return hashes.compute(key, (ignored, previous) -> {
                try {
                    if (previous != null && previous.size() == attrs.size()
                            && previous.modified().equals(attrs.lastModifiedTime())
                            && previous.created().equals(attrs.creationTime())
                            && Objects.equals(previous.fileKey(), attrs.fileKey())) return previous;
                    return new Snapshot(attrs.size(), attrs.lastModifiedTime(), attrs.creationTime(), attrs.fileKey(), digestFile(key));
                } catch (IOException e) { throw new UncheckedIOException(e); }
            }).hash();
        } catch (UncheckedIOException e) { throw e.getCause(); }
    }

    public void invalidate(Path file) { hashes.remove(file.toAbsolutePath().normalize()); }
    public void clear() { hashes.clear(); }

    private String digestFile(Path key) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(key)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String hashCommand(List<String> command) {
        MessageDigest digest = newDigest();
        for (String part : command) {
            digest.update(part.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
