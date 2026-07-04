package dtm.builder.build;

import java.nio.file.Path;

public record BuildResult(boolean success, int exitCode, Path artifact, String message) {

    public static BuildResult ok(int exitCode, Path artifact, String message) {
        return new BuildResult(true, exitCode, artifact, message);
    }

    public static BuildResult fail(int exitCode, String message) {
        return new BuildResult(false, exitCode, null, message);
    }
}
