package dtm.builder.build.graph;

import dtm.builder.build.TargetType;
import dtm.builder.manifest.ManifestParser;
import dtm.builder.manifest.ManifestProfiles;
import dtm.builder.manifest.model.ManifestRootModel;
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
                  "sources": ["src/shared"],
                  "includes": ["include"],
                  "defines": ["BASE=1"],
                  "compileFlags": ["-Wall"],
                  "targets": [
                    { "id": "core", "type": "shared",
                      "sources": ["src/core"],
                      "defines": ["CORE=1"],
                      "compileFlags": ["-fvisibility=hidden"] }
                  ] }
                """), PROJECT, false);
        assertTrue(r.isOk(), String.join("; ", r.errors()));
        assertTrue(r.multiTarget());
        ResolvedTarget core = r.targets().get(0);
        assertEquals(List.of("src/shared", "src/core"), core.sources());
        assertEquals(List.of("include"), core.includes());
        assertEquals(List.of("BASE=1", "CORE=1"), core.defines());
        assertEquals(List.of("-Wall", "-fvisibility=hidden"), core.compileFlags());
    }

    @Test
    void excludeRemovesInheritedRootSources() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sources": ["src/shared", "src/legacy"],
                  "targets": [
                    { "id": "core", "type": "shared",
                      "sources": ["src/core"],
                      "excludeSources": ["src/legacy"] }
                  ] }
                """), PROJECT, false);
        assertTrue(r.isOk());
        assertEquals(List.of("src/shared", "src/core"), r.targets().get(0).sources());
    }

    @Test
    void profileTargetOverrideMergesById() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sources": ["src/shared"],
                  "activeProfile": "windows",
                  "targets": [
                    { "id": "runtime", "sources": ["src/runtime"], "defines": ["A=1"] }
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
        assertEquals(List.of("src/shared", "src/runtime"), runtime.sources());
    }

    @Test
    void profileCanAddNewTarget() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "vema", "version": "1",
                  "sources": ["src/shared"],
                  "activeProfile": "linux",
                  "targets": [ { "id": "runtime", "sources": ["src/runtime"] } ],
                  "profiles": {
                    "linux": {
                      "targets": [ { "id": "posix-shim", "type": "static",
                                     "sources": ["src/posix"] } ]
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
                { "id": "x", "version": "1", "sources": ["src"],
                  "targets": [ { "id": "a", "dependsOn": ["nope"] } ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("nope"));
    }

    @Test
    void reportsDependencyOnExecutable() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1", "sources": ["src"],
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
        assertTrue(r.errors().get(0).contains("sources"));
    }

    @Test
    void reportsArtifactNameCollision() {
        TargetResolution r = TargetResolver.resolve(manifest("""
                { "id": "x", "version": "1", "sources": ["src"],
                  "targets": [
                    { "id": "a", "name": "app" },
                    { "id": "b", "name": "app" }
                  ] }
                """), PROJECT, false);
        assertFalse(r.isOk());
        assertTrue(r.errors().get(0).contains("mesmo artefato"));
    }
}
