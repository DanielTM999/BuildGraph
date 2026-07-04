package dtm.builder.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@FunctionalInterface
public interface ProcessExecutor {

    ProcessExecutor REAL = ProcessRunner::run;

    int run(List<String> command, Path workingDir, Map<String, String> env,
            Consumer<String> output);
}
