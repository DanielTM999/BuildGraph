package dtm.builder.build;

import dtm.builder.build.graph.ResolvedTarget;
import dtm.builder.build.graph.TargetResolver;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TargetPlatformToolchainTest {
    @TempDir Path project;

    private static Toolchain toolchain(ToolchainKind kind) {
        return new Toolchain(kind, Path.of("cc"), Path.of("c++"));
    }

    @Test void mingwTurnsShortWindowsPlatformsIntoGnuTriples() {
        Toolchain mingw = toolchain(ToolchainKind.MINGW);
        assertEquals("x86_64-w64-mingw32", TargetPlatform.forToolchain("windows-x86_64", mingw));
        assertEquals("x86_64-w64-mingw32", TargetPlatform.forToolchain(" Windows-AMD64 ", mingw));
        assertEquals("i686-w64-mingw32", TargetPlatform.forToolchain("windows-x86", mingw));
        assertEquals("aarch64-w64-mingw32", TargetPlatform.forToolchain("windows-arm64", mingw));
        var platform = TargetPlatform.resolve(TargetPlatform.forToolchain("windows-x86_64", mingw), null);
        assertTrue(platform.windows());
        assertFalse(platform.triple().contains("msvc"));
        assertEquals("libcore.a", platform.fileName("core", TargetType.STATIC, false));
    }

    @Test void explicitTriplesOtherToolchainsAndOtherSystemsStayUntouched() {
        Toolchain mingw = toolchain(ToolchainKind.MINGW);
        assertEquals("x86_64-pc-windows-msvc", TargetPlatform.forToolchain("x86_64-pc-windows-msvc", mingw));
        assertEquals("linux-x86_64", TargetPlatform.forToolchain("linux-x86_64", mingw));
        assertEquals("windows-riscv", TargetPlatform.forToolchain("windows-riscv", mingw));
        assertNull(TargetPlatform.forToolchain(null, mingw));
        for (ToolchainKind kind : List.of(ToolchainKind.MSVC, ToolchainKind.SYSTEM_CLANG, ToolchainKind.GCC))
            assertEquals("windows-x86_64", TargetPlatform.forToolchain("windows-x86_64", toolchain(kind)));
        assertEquals("windows-x86_64", TargetPlatform.forToolchain("windows-x86_64", null));
    }

    @Test void adaptedPlatformNamesStaticLibrariesTheGnuWay() throws Exception {
        Files.createDirectories(project.resolve("src/core"));
        Files.writeString(project.resolve("src/core/core.cpp"), "int f() { return 1; }");
        ManifestTargetModel core = new ManifestTargetModel();
        core.setId("core");
        core.setType("static");
        core.setSources(List.of("src/core"));
        ManifestRootModel m = new ManifestRootModel();
        m.setTargets(List.of(core));

        m.setPlatform("windows-x86_64");
        ResolvedTarget msvcTarget = TargetResolver.resolve(m, project, false).targets().getFirst();
        assertEquals("core.lib", NativeArtifacts.artifact(project, m, msvcTarget, false).getFileName().toString());

        m.setPlatform(TargetPlatform.forToolchain("windows-x86_64", toolchain(ToolchainKind.MINGW)));
        ResolvedTarget gnuTarget = TargetResolver.resolve(m, project, false).targets().getFirst();
        assertEquals("libcore.a", NativeArtifacts.artifact(project, m, gnuTarget, false).getFileName().toString());
    }
}
