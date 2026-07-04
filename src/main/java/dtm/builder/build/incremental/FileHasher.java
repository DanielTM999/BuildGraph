package dtm.builder.build.incremental;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class FileHasher {

    /** Carimbo "tamanho|mtime|sha256"; tamanho+mtime servem de fast-path na comparacao. */
    public String stamp(Path file) throws IOException {
        Path key = file.toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(key, BasicFileAttributes.class);
        return attrs.size() + "|" + attrs.lastModifiedTime().toMillis() + "|" + sha256(key);
    }

    /** true se o conteudo do arquivo ainda corresponde ao carimbo gravado. */
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
            if (String.valueOf(attrs.size()).equals(parts[0])
                    && String.valueOf(attrs.lastModifiedTime().toMillis()).equals(parts[1])) {
                return true;
            }
            return sha256(key).equals(parts[2]);
        } catch (IOException e) {
            return false;
        }
    }

    public String sha256(Path file) throws IOException {
        Path key = file.toAbsolutePath().normalize();
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
