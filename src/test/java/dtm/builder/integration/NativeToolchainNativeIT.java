package dtm.builder.integration;

import dtm.builder.build.ToolProbe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class NativeToolchainNativeIT {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    @TempDir
    Path temp;

    @BeforeAll
    static void requireConfiguredToolchain() {
        assertTrue(ToolProbe.findOnPath(cCompiler()) != null,
                "Compilador C indisponivel no PATH: " + cCompiler());
        assertTrue(ToolProbe.findOnPath(cxxCompiler()) != null,
                "Compilador C++ indisponivel no PATH: " + cxxCompiler());
    }

    @Test
    void buildsRunsAndInvalidatesHeaderIncrementally() throws Exception {
        Path project = Files.createDirectories(temp.resolve("simple"));
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("value.h"), "#define VALUE 0\n");
        Files.writeString(source.resolve("main.c"), """
                #include "value.h"
                int main(void) { return VALUE; }
                """);
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "id":"simple",
                  "name":"simple",
                  "version":"1.0.0",
                  "cCompiler":"%s",
                  "cxxCompiler":"%s",
                  "sourceFolders":["src"]
                }
                """.formatted(cCompiler(), cxxCompiler()));

        Result first = build(project);
        assertEquals(0, first.exitCode(), first.output());
        assertEquals(0, runExecutable(artifact(project, "simple")).exitCode());

        Result second = build(project);
        assertEquals(0, second.exitCode(), second.output());
        assertTrue(second.output().contains("Sem mudancas"), second.output());
        assertTrue(second.output().contains("Artefato ja atualizado"), second.output());

        Files.writeString(source.resolve("value.h"), "#define VALUE 0 /* changed */\n");
        Result third = build(project);
        assertEquals(0, third.exitCode(), third.output());
        assertTrue(third.output().contains("Compilado"), third.output());
        assertEquals(0, runExecutable(artifact(project, "simple")).exitCode());
    }

    @Test
    void buildsAndRunsStaticTargetGraph() throws Exception {
        Path project = Files.createDirectories(temp.resolve("targets"));
        Path include = Files.createDirectories(project.resolve("include"));
        Path library = Files.createDirectories(project.resolve("lib"));
        Path app = Files.createDirectories(project.resolve("app"));
        Files.writeString(include.resolve("answer.h"), "int answer(void);\n");
        Files.writeString(library.resolve("answer.c"), "int answer(void) { return 0; }\n");
        Files.writeString(app.resolve("main.c"), """
                #include "answer.h"
                int main(void) { return answer(); }
                """);
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "id":"targets",
                  "name":"targets",
                  "version":"1.0.0",
                  "cCompiler":"%s",
                  "cxxCompiler":"%s",
                  "includePaths":["include"],
                  "targets":[
                    {"id":"answer","type":"static","sourceFolders":["lib"]},
                    {"id":"app","type":"executable","sourceFolders":["app"],
                     "dependsOn":["answer"]}
                  ]
                }
                """.formatted(cCompiler(), cxxCompiler()));

        Result result = build(project);

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(0, runExecutable(artifact(project, "app")).exitCode());
    }

    @Test
    void buildsAndRunsCppExecutable() throws Exception {
        Path project = Files.createDirectories(temp.resolve("cpp"));
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.cpp"), """
                namespace sample { constexpr int result() { return 0; } }
                int main() { return sample::result(); }
                """);
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "id":"cpp-app",
                  "name":"cpp-app",
                  "version":"1.0.0",
                  "cCompiler":"%s",
                  "cxxCompiler":"%s",
                  "cxxStandard":"c++17",
                  "sourceFolders":["src"]
                }
                """.formatted(cCompiler(), cxxCompiler()));

        Result result = build(project);

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(0, runExecutable(artifact(project, "cpp-app")).exitCode());
    }

    @Test
    void buildsAndRunsSharedTargetGraphOnUnixToolchains() throws Exception {
        assumeFalse(isWindows(), "Shared MSVC possui fluxo especifico de import library");
        Path project = Files.createDirectories(temp.resolve("shared"));
        Path include = Files.createDirectories(project.resolve("include"));
        Path library = Files.createDirectories(project.resolve("lib"));
        Path app = Files.createDirectories(project.resolve("app"));
        Files.writeString(include.resolve("shared_answer.h"), "int shared_answer(void);\n");
        Files.writeString(library.resolve("shared_answer.c"),
                "int shared_answer(void) { return 0; }\n");
        Files.writeString(app.resolve("main.c"), """
                #include "shared_answer.h"
                int main(void) { return shared_answer(); }
                """);
        String rpath = isMac() ? "-Wl,-rpath,@loader_path" : "-Wl,-rpath,$ORIGIN";
        Files.writeString(project.resolve("Manifest.json"), """
                {
                  "id":"shared-targets",
                  "name":"shared-targets",
                  "version":"1.0.0",
                  "cCompiler":"%s",
                  "cxxCompiler":"%s",
                  "includePaths":["include"],
                  "linkFlags":["%s"],
                  "targets":[
                    {"id":"shared_answer","type":"shared","sourceFolders":["lib"]},
                    {"id":"shared_app","type":"executable","sourceFolders":["app"],
                     "dependsOn":["shared_answer"]}
                  ]
                }
                """.formatted(cCompiler(), cxxCompiler(), rpath));

        Result result = build(project);

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(0, runExecutable(artifact(project, "shared_app")).exitCode());
    }

    private Result build(Path project) throws Exception {
        return run(List.of(javaExecutable(), "-jar", buildGraphJar().toString(),
                project.toString(), "build"), project);
    }

    private Result runExecutable(Path executable) throws Exception {
        assertTrue(Files.isRegularFile(executable), "Artefato ausente: " + executable);
        return run(List.of(executable.toString()), executable.getParent());
    }

    private Result run(List<String> command, Path workingDirectory) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(new ArrayList<>(command))
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new AssertionError("Toolchain/comando indisponivel: " + command.get(0), e);
        }
        boolean completed = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("Timeout executando: " + String.join(" ", command));
        }
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new Result(process.exitValue(), output);
    }

    private static String cCompiler() {
        String compiler = System.getProperty("buildgraph.it.cCompiler", "").trim();
        if (compiler.isEmpty()) {
            throw new AssertionError("Ative native-it-gcc, native-it-clang ou native-it-msvc");
        }
        return compiler;
    }

    private static String cxxCompiler() {
        String compiler = System.getProperty("buildgraph.it.cxxCompiler", "").trim();
        if (compiler.isEmpty()) {
            throw new AssertionError("Driver C++ nao configurado no profile de integracao");
        }
        return compiler;
    }

    private static Path buildGraphJar() {
        Path jar = Path.of("target", "BuildGraph.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), "Execute os testes pela fase Maven verify: " + jar);
        return jar;
    }

    private static String javaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static Path artifact(Path project, String name) {
        return project.resolve("build").resolve(isWindows() ? name + ".exe" : name);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private record Result(int exitCode, String output) {
    }
}
