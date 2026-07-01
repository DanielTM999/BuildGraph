package dtm.bulder.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ProcessRunner {

    public static final int COMMAND_NOT_FOUND = 127;

    private ProcessRunner() {
    }

    public static int run(List<String> command, Path workingDir, Map<String, String> env,
                          Consumer<String> output) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(true);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            emit(output, "Comando nao encontrado ou falhou ao iniciar: "
                    + String.join(" ", command) + " (" + e.getMessage() + ")");
            return COMMAND_NOT_FOUND;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emit(output, line);
            }
        } catch (IOException e) {
            emit(output, "Erro lendo saida do processo: " + e.getMessage());
        }

        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            return -1;
        }
    }

    private static void emit(Consumer<String> output, String line) {
        if (output != null) {
            output.accept(line);
        }
    }
}
