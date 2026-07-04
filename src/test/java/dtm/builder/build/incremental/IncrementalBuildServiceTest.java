package dtm.builder.build.incremental;

import dtm.builder.build.Toolchain;
import dtm.builder.build.ToolchainKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalBuildServiceTest {

    @TempDir
    Path temp;

    @Test
    void invalidatesObjectWhenSourceHeaderOrCommandChanges() throws Exception {
        Path compiler = Files.writeString(temp.resolve("cc"), "compiler");
        Path source = Files.writeString(temp.resolve("main.c"), "#include \"value.h\"");
        Path header = Files.writeString(temp.resolve("value.h"), "#define VALUE 1");
        Path object = Files.writeString(temp.resolve("main.o"), "object");
        List<String> command = List.of(compiler.toString(), "-c", source.toString());
        Toolchain toolchain = new Toolchain(ToolchainKind.CUSTOM, compiler, compiler);

        IncrementalBuildService first = new IncrementalBuildService(temp, toolchain,
                "Debug", true);
        TargetState initial = first.begin("app");
        first.recordCompiled(initial, source, object, command, List.of(header));
        first.finish(initial, null);

        IncrementalBuildService second = new IncrementalBuildService(temp, toolchain,
                "Debug", true);
        assertTrue(second.isObjectUpToDate(second.begin("app"), source, object, command));
        assertFalse(second.isObjectUpToDate(second.begin("app"), source, object,
                List.of(compiler.toString(), "-O2", "-c", source.toString())));

        Files.writeString(header, "#define VALUE 2");
        assertFalse(second.isObjectUpToDate(second.begin("app"), source, object, command));
    }

    @Test
    void invalidatesLinkWhenAnInputChanges() throws Exception {
        Path compiler = Files.writeString(temp.resolve("cc"), "compiler");
        Path object = Files.writeString(temp.resolve("main.o"), "object-v1");
        Path artifact = Files.writeString(temp.resolve("app"), "binary");
        List<String> command = List.of(compiler.toString(), object.toString(), "-o",
                artifact.toString());
        Toolchain toolchain = new Toolchain(ToolchainKind.CUSTOM, compiler, compiler);

        IncrementalBuildService first = new IncrementalBuildService(temp, toolchain,
                "Debug", true);
        TargetState initial = first.begin("app");
        first.recordLinked(initial, command, List.of(object));
        first.finish(initial, null);

        IncrementalBuildService second = new IncrementalBuildService(temp, toolchain,
                "Debug", true);
        assertFalse(second.needsLink(second.begin("app"), command, artifact, List.of(object)));
        Files.writeString(object, "object-v2");
        assertTrue(second.needsLink(second.begin("app"), command, artifact, List.of(object)));
    }

    @Test
    void resolvesRelativeDepFileEntriesAgainstCompilerWorkingDirectory() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path depFile = Files.writeString(temp.resolve("main.o.d"),
                "main.o: src/main.c include/value.h\n");

        List<Path> dependencies = DepFileParser.parseSafe(depFile, project);

        assertTrue(dependencies.contains(project.resolve("src/main.c")));
        assertTrue(dependencies.contains(project.resolve("include/value.h")));
    }
}
