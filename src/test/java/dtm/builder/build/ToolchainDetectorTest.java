package dtm.builder.build;

import dtm.builder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolchainDetectorTest {
    @Test
    void resolvesEachLanguageWithManifestThenSpecificThenCommon() {
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setCCompiler("manifest-gcc");
        Toolchain tc = ToolchainDetector.resolve(manifest, "common", "cli-gcc", "cli-g++", Path.of("."));
        assertEquals(Path.of("manifest-gcc"), tc.cc());
        assertEquals(Path.of("cli-g++"), tc.cxx());
        tc = ToolchainDetector.resolve(new ManifestRootModel(), "common", "cli-gcc", null, Path.of("."));
        assertEquals(Path.of("cli-gcc"), tc.cc());
        assertEquals(Path.of("common"), tc.cxx());
    }

    @Test
    void resolvesProjectRelativeCompilers() {
        Toolchain tc = ToolchainDetector.resolve(new ManifestRootModel(), null,
                "tools/gcc", "tools/g++", Path.of("project"));
        assertEquals(Path.of("project/tools/gcc"), tc.cc());
        assertEquals(Path.of("project/tools/g++"), tc.cxx());
    }


    @Test
    void manifestCompilerWinsOverCliCompiler() {
        ManifestRootModel manifest = new ManifestRootModel();
        manifest.setCCompiler("manifest-cc-not-on-path");

        Toolchain toolchain = ToolchainDetector.resolve(manifest, "cli-cc-not-on-path");

        assertEquals(Path.of("manifest-cc-not-on-path"), toolchain.cc());
    }

    @Test
    void cliCompilerIsFallbackWhenManifestHasNone() {
        Toolchain toolchain = ToolchainDetector.resolve(new ManifestRootModel(),
                "cli-cc-not-on-path");

        assertEquals(Path.of("cli-cc-not-on-path"), toolchain.cc());
        assertEquals(toolchain.cc(), toolchain.cxx());
    }

    @Test
    void recognizesCompilerFamilyFromConfiguredExecutable() {
        ManifestRootModel clangManifest = new ManifestRootModel();
        clangManifest.setCCompiler("clang");
        clangManifest.setCxxCompiler("clang++");
        ManifestRootModel msvcManifest = new ManifestRootModel();
        msvcManifest.setCCompiler("cl.exe");

        assertEquals(ToolchainKind.SYSTEM_CLANG,
                ToolchainDetector.resolve(clangManifest, null).kind());
        assertEquals(ToolchainKind.MSVC,
                ToolchainDetector.resolve(msvcManifest, null).kind());
    }
}
