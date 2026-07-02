package dtm.bulder.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticLibSupportTest {

    @Test
    void archiveCommandForArStyle() {
        List<String> cmd = ArchiverCommandBuilder.buildArchiveCommand(
                Path.of("/usr/bin/llvm-ar"), Path.of("/proj/build/libcore.a"),
                List.of(Path.of("/proj/build/.obj/core/a.o"), Path.of("/proj/build/.obj/core/b.o")),
                false);
        assertEquals(Path.of("/usr/bin/llvm-ar").toString(), cmd.get(0));
        assertEquals("rcs", cmd.get(1));
        assertEquals(Path.of("/proj/build/libcore.a").toString(), cmd.get(2));
        assertEquals(5, cmd.size());
    }

    @Test
    void archiveCommandForMsvc() {
        List<String> cmd = ArchiverCommandBuilder.buildArchiveCommand(
                Path.of("C:/vs/lib.exe"), Path.of("C:/proj/build/core.lib"),
                List.of(Path.of("C:/proj/build/.obj/core/a.obj")), true);
        assertEquals("/nologo", cmd.get(1));
        assertTrue(cmd.get(2).startsWith("/OUT:"));
        assertTrue(cmd.get(2).endsWith("core.lib"));
    }

    @Test
    void staticArtifactNaming() {
        assertEquals("libcore.a", Artifacts.fileName("core", TargetType.STATIC, false));
        assertEquals("core.lib", Artifacts.fileName("core", TargetType.STATIC, true));
    }

    @Test
    void sharedAndExecutableNamingKeepLegacyBehavior() {
        String exe = Artifacts.fileName("app", TargetType.EXECUTABLE, false);
        String shared = Artifacts.fileName("core", TargetType.SHARED, false);
        if (ToolProbe.isWindows()) {
            assertEquals("app.exe", exe);
            assertEquals("core.dll", shared);
        } else {
            assertEquals("app", exe);
            assertTrue(shared.startsWith("libcore."));
        }
    }

    @Test
    void archiverIsFoundBesideClangDriver(@TempDir Path dir) throws IOException {
        Path clang = dir.resolve(ToolProbe.isWindows() ? "clang++.exe" : "clang++");
        Path llvmAr = dir.resolve(ToolProbe.isWindows() ? "llvm-ar.exe" : "llvm-ar");
        Files.createFile(clang);
        Files.createFile(llvmAr);

        Toolchain toolchain = new Toolchain(ToolchainKind.SYSTEM_CLANG, clang, clang);
        assertEquals(llvmAr, ToolchainDetector.resolveArchiver(toolchain));
    }
}
