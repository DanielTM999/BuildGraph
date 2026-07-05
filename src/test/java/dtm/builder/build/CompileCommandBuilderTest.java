package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileCommandBuilderTest {

    private ManifestRootModel manifest() {
        ManifestRootModel m = new ManifestRootModel();
        m.setName("app");
        m.setCxxStandard("cpp20");
        m.setIncludes(new java.util.ArrayList<>(List.of("include")));
        m.setDefines(new java.util.ArrayList<>(List.of("APP=1")));
        return m;
    }

    private CompileSpec spec(boolean library, String buildMode) {
        Toolchain toolchain = new Toolchain(ToolchainKind.GCC, Path.of("gcc"), Path.of("g++"));
        return new CompileSpec(toolchain, true, manifest(), library,
                List.of(Path.of("src/main.cpp")), Path.of("out/app"),
                List.of(Path.of("pkg/include")), List.of(), List.of("fmt"),
                buildMode, List.of(), Path.of("."));
    }

    @Test
    void buildsReleaseExecutableCommand() {
        List<String> cmd = CompileCommandBuilder.buildCompileCommand(spec(false, "Release"));
        assertTrue(cmd.contains("-std=c++20"));
        assertTrue(cmd.contains("-O2"));
        assertTrue(cmd.contains("-DNDEBUG"));
        assertTrue(cmd.contains("-DAPP=1"));
        assertTrue(cmd.contains("-lfmt"));
        assertTrue(cmd.contains("-o"));
        assertFalse(cmd.contains("-shared"));
        assertTrue(cmd.stream().anyMatch(a -> a.startsWith("-I")));
    }

    @Test
    void buildsDebugLibraryCommand() {
        List<String> cmd = CompileCommandBuilder.buildCompileCommand(spec(true, "Debug"));
        assertTrue(cmd.contains("-O0"));
        assertTrue(cmd.contains("-g"));
        assertTrue(cmd.contains("-shared"));
    }

    @Test
    void buildsLinkCommandFromObjectFiles() {
        CompileSpec linkSpec = new CompileSpec(
                new Toolchain(ToolchainKind.GCC, Path.of("gcc"), Path.of("g++")),
                true, manifest(), false, List.of(Path.of("out/main.o"), Path.of("out/app.o")),
                Path.of("out/app"), List.of(), List.of(), List.of(), "Debug", List.of(),
                Path.of("."));

        List<String> cmd = CompileCommandBuilder.buildLinkCommand(linkSpec);

        assertTrue(cmd.contains(Path.of("out/main.o").toString()));
        assertTrue(cmd.contains(Path.of("out/app.o").toString()));
        assertTrue(cmd.contains("-o"));
        assertFalse(cmd.contains("-c"));
    }
}
