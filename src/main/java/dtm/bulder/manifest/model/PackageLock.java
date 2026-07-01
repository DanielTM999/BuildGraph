package dtm.bulder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageLock {

    public static final String FILE_NAME = ".package-lock.json";
    public static final String MANAGED_BY = "buildgraph";

    private String id;
    private String version;
    private String name;
    private String source;
    private String kind;
    private String platformToken;
    private String managedBy;
    private String globalManifestPath;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPlatformToken() {
        return platformToken;
    }

    public void setPlatformToken(String platformToken) {
        this.platformToken = platformToken;
    }

    public String getManagedBy() {
        return managedBy;
    }

    public void setManagedBy(String managedBy) {
        this.managedBy = managedBy;
    }

    public String getGlobalManifestPath() {
        return globalManifestPath;
    }

    public void setGlobalManifestPath(String globalManifestPath) {
        this.globalManifestPath = globalManifestPath;
    }
}
