package dtm.builder.build.cache;

import dtm.builder.build.incremental.FileHasher;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SharedObjectCache {
    private static final int MAGIC = 0x42474f31;
    private final Path root;
    private final long limit;
    private final AtomicBoolean written = new AtomicBoolean();
    private final AtomicBoolean used = new AtomicBoolean();

    public SharedObjectCache(Path root, long limit) {
        this.root = root.toAbsolutePath().normalize();
        this.limit = limit;
    }
    public Path entry(String key) {
        if (!key.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid object cache key");
        return root.resolve(key.substring(0, 2)).resolve(key + ".entry");
    }

    public boolean restore(String key, Path object) {
        used.set(true);
        Path temporary = null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(entry(key))))) {
            if (input.readInt() != MAGIC || !input.readUTF().equals(key)) return false;
            String digest = input.readUTF();
            long size = input.readLong();
            if (size <= 0 || size > limit || size > Files.size(entry(key))) return false;
            Files.createDirectories(object.toAbsolutePath().getParent());
            temporary = Files.createTempFile(object.toAbsolutePath().getParent(), ".cache-", ".tmp");
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                byte[] buffer = new byte[64 * 1024];
                long left = size;
                while (left > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, left));
                    if (read < 0) return false;
                    output.write(buffer, 0, read);
                    left -= read;
                }
            }
            if (input.read() != -1 || !new FileHasher().sha256(temporary).equals(digest)) return false;
            atomicMove(temporary, object);
            try { Files.setLastModifiedTime(entry(key), FileTime.fromMillis(System.currentTimeMillis())); }
            catch (IOException ignored) { }
            return true;
        } catch (IOException | RuntimeException e) { return false; }
        finally { delete(temporary); }
    }

    public void store(String key, Path object) {
        used.set(true);
        Path temporary = null;
        try {
            long size = Files.size(object);
            if (size <= 0 || size + 256 > limit) return;
            Path destination = entry(key);
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), ".write-", ".tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeUTF(key);
                output.writeUTF(new FileHasher().sha256(object));
                output.writeLong(size);
                Files.copy(object, output);
            }
            atomicMove(temporary, destination);
            written.set(true);
        } catch (IOException | RuntimeException ignored) { }
        finally { delete(temporary); }
    }

    public void prune() {
        if (!written.get() && !used.get()) return;
        try {
            Files.createDirectories(root);
            try (FileChannel channel = FileChannel.open(root.resolve("eviction.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.tryLock()) {
                if (lock == null) return;
                record Item(Path path, long size, long accessed) { }
                List<Item> entries = new ArrayList<>();
                long total = 0;
                try (var paths = Files.walk(root, 2)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String name = path.getFileName().toString();
                        String shard = path.getParent().getFileName().toString();
                        if (name.matches("[a-f0-9]{64}\\.entry") && name.startsWith(shard) && shard.matches("[a-f0-9]{2}")) {
                            var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                            entries.add(new Item(path, attributes.size(), attributes.lastModifiedTime().toMillis()));
                            total += attributes.size();
                        } else if (name.startsWith(".write-") && name.endsWith(".tmp") && shard.matches("[a-f0-9]{2}")
                                && Files.getLastModifiedTime(path).toMillis() < System.currentTimeMillis() - 86_400_000L) delete(path);
                    }
                }
                entries.sort(Comparator.comparingLong(Item::accessed));
                for (Item item : entries) {
                    if (total <= limit) break;
                    try { if (Files.deleteIfExists(item.path())) total -= item.size(); } catch (IOException ignored) { }
                }
            }
        } catch (IOException | RuntimeException ignored) { }
    }

    public static void atomicMove(Path from, Path to) throws IOException {

        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private static void delete(Path file) {
        if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }
}
