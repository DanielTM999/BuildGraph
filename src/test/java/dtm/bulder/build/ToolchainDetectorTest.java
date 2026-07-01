package dtm.bulder.build;

import dtm.bulder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolchainDetectorTest {

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
