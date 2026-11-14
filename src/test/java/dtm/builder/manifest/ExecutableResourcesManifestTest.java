package dtm.builder.manifest;

import dtm.builder.build.graph.TargetResolver;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ExecutableResourcesManifestTest {
    @Test void profileAndTargetOverridesMergeByKeyAndHonorNulls() {
        var parsed = ManifestParser.readManifest("""
                {"name":"Vertex", "icon":"base.png", "metadata":{"productName":"Vertex","description":"Base","author":"A"},
                 "activeProfile":"debug", "profiles":{"debug":{
                   "metadata":{"description":"Debug","author":null},
                   "targets":[{"id":"app","metadata":{"description":"Profile target"}}]}},
                 "targets":[{"id":"app","type":"executable","sources":["main.c"],
                             "icon":null,"metadata":{"companyName":"Vendor"}},
                            {"id":"other","type":"executable","sources":["main.c"]}]}
                """, false);
        assertTrue(parsed.isOk(), parsed.getDiagnostics().toString());
        var effective = ManifestProfiles.effective(parsed.getManifest());
        var resolved = TargetResolver.resolve(effective, Path.of("."), false);
        var app = TargetResolver.perTargetManifest(effective, TargetResolver.byId(resolved, "app"));
        var other = TargetResolver.perTargetManifest(effective, TargetResolver.byId(resolved, "other"));
        assertNull(app.getIcon());
        assertEquals("base.png", other.getIcon());
        assertEquals("Vertex", app.getMetadata().get("productName"));
        assertEquals("Profile target", app.getMetadata().get("description"));
        assertEquals("Vendor", app.getMetadata().get("companyName"));
        assertNull(app.getMetadata().get("author"));
    }

    @Test void oldManifestKeepsResourcesAbsent() {
        var parsed = ManifestParser.readManifest("{\"name\":\"old\",\"sources\":[\"src\"]}", false);
        assertTrue(parsed.isOk());
        assertNull(parsed.getManifest().getIcon());
        assertTrue(parsed.getManifest().getMetadata().isEmpty());
    }
}
