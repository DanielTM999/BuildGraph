package dtm.bulder.build.graph;

import dtm.bulder.build.TargetType;
import dtm.bulder.manifest.ManifestParser;
import dtm.bulder.manifest.ManifestProfiles;
import dtm.bulder.manifest.model.ManifestRootModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetResolverTest {

    private static final Path PROJECT = Path.of("C:/proj/vema");

    private static ManifestRootModel manifest(String json) {
        ManifestRootModel raw = ManifestParser.readManifest(json, false).getManifest();
        return ManifestProfiles.effective(raw);
    }

    @Test
    void legacyManifestYieldsSingleSyntheticTarget() {
        TargetResolution r = TargetResolver.resolve(
                manifest("{ \"id\": \"hello\", \"version\": \"1\", \"library\": true }"),
                PROJECT, false);
        assertTrue(r.isOk());
        assertFalse(r.multiTarget());
        assertEquals(1, r.targets().size());
        ResolvedTarget t = r.targets().get(0);
        assertTrue(t.synthetic());
        assertEquals("hello", t.id());
        assertEquals(TargetType.SHARED, t.type());
    }

    @Test
    void targetsInheritRootListsAdditively() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sourceFolders": ["src/shared"],
                  "includePaths": ["include"],
                  "defines": ["BASE=1"],
                  "compileFlags": ["-Wall"],
                  "targets": [
                    { "id": "core", "type": "shared",
                      "sourceFolders": ["src/core"],
                      "defines": ["CORE=1"],
                      "compileFlags": ["-fvisibility=hidden"] }
                  ] }
                """), PROJECT, false);
        assertTrue(r.isOk(), String.join("; ", r.errors()));
        assertTrue(r.multiTarget());
        ResolvedTarget core = r.targets().get(0);
        assertEquals(List.of("src/shared", "src/core"), core.sourceFolders());
        assertEquals(List.of("include"), core.includePaths());
        assertEquals(List.of("BASE=1", "CORE=1"), core.defines());
        assertEquals(List.of("-Wall", "-fvisibility=hidden"), core.compileFlags());
    }

    @Test
    void excludeRemovesInheritedRootSources() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sourceFolders": ["src/shared", "src/legacy"],
                  "targets": [
                    { "id": "core", "type": "shared",
                      "sourceFolders": ["src/core"],
                      "excludeSourceFolders": ["src/legacy"] }
                  ] }
                """), PROJECT, false);
        assertTrue(r.isOk());
        assertEquals(List.of("src/shared", "src/core"), r.targets().get(0).sourceFolders());
    }

    @Test
    void profileTargetOverrideMergesById() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sourceFolders": ["src/shared"],
                  "activeProfile": "windows",
                  "targets": [
                    { "id": "runtime", "sourceFolders": ["src/runtime"], "defines": ["A=1"] }
                  ],
                  "profiles": {
                    "windows": {
                      "defines": ["OS_WIN=1"],
                      "targets": [ { "id": "runtime", "defines": ["VEMA_WIN32"] } ]
                    }
                  } }
                """), PROJECT, false);
        assertTrue(r.isOk(), String.join("; ", r.errors()));
        ResolvedTarget runtime = r.targets().get(0);
        assertEquals(List.of("OS_WIN=1", "A=1", "VEMA_WIN32"), runtime.defines());
        assertEquals(List.of("src/shared", "src/runtime"), runtime.sourceFolders());
    }

    @Test
    void profileCanAddNewTarget() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sourceFolders": ["src/shared"],
                  "activeProfile": "linux",
                  "targets": [ { "id": "runtime", "sourceFolders": ["src/runtime"] } ],
                  "profiles": {
                    "linux": {
                      "targets": [ { "id": "posix-shim", "type": "static",
                                     "sourceFolders": ["src/posix"] } ]
                    }
                  } }
                """), PROJECT, false);
        assertTrue(r.isOk(), String.join("; ", r.errors()));
        assertEquals(2, r.targets().size());
        assertEquals("posix-shim", r.targets().get(1).id());
        assertEquals(TargetType.STATIC, r.targets().get(1).type());
    }

    @Test
    void reportsUnknownDependency() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1", "sourceFolders": ["src"],
                  "targets": [ { "id": "a", "dependsOn": ["nope"] } ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("nope"));
    }

    @Test
    void reportsDependencyOnExecutable() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1", "sourceFolders": ["src"],
                  "targets": [
                    { "id": "tool" },
                    { "id": "app", "dependsOn": ["tool"] }
                  ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("executavel"));
    }

    @Test
    void reportsTargetWithoutEffectiveSources() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1",
                  "targets": [ { "id": "a" } ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("sourceFolders"));
    }

    @Test
    void reportsArtifactNameCollision() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1", "sourceFolders": ["src"],
                  "targets": [
                    { "id": "a", "name": "app" },
                    { "id": "b", "name": "app" }
                  ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("mesmo artefato"));
    }
}
