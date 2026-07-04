package dtm.builder.build.incremental;

import com.fasterxml.jackson.databind.ObjectMapper;
import dtm.builder.repo.PathSanitizer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BuildStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BuildStateStore() {
    }

    /** Fica dentro de buildDir de proposito: a fase clean ja o remove junto. */
    public static Path stateDir(Path buildDir) {
        return buildDir.resolve(".buildgraph-state");
    }

    /** null se ausente, corrompido ou de schema incompativel (forca rebuild do target). */
    public static TargetBuildState load(Path buildDir, String targetId) {
        Path file = stateFile(buildDir, targetId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            TargetBuildState state = MAPPER.readValue(file.toFile(), TargetBuildState.class);
            if (state == null || state.getSchemaVersion() != TargetBuildState.SCHEMA_VERSION) {
                return null;
            }
            return state;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static void save(Path buildDir, String targetId, TargetBuildState state)
            throws IOException {
        Path file = stateFile(buildDir, targetId);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), state);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path stateFile(Path buildDir, String targetId) {
        return stateDir(buildDir).resolve(PathSanitizer.sanitizeId(targetId) + ".json");
    }
}
