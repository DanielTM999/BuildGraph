package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyLock {

    public static final String FILE_NAME = "BuildGraph.lock.json";
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion = SCHEMA_VERSION;
    private String packagesFingerprint = "";
    private List<LockedDependency> packages = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getPackagesFingerprint() {
        return packagesFingerprint;
    }

    public void setPackagesFingerprint(String packagesFingerprint) {
        this.packagesFingerprint = packagesFingerprint == null ? "" : packagesFingerprint;
    }

    public List<LockedDependency> getPackages() {
        return packages;
    }

    public void setPackages(List<LockedDependency> packages) {
        this.packages = packages == null ? new ArrayList<>() : packages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LockedDependency {
        private String id;
        private String version;
        private String variant;
        private String downloadUrl;
        private List<String> dependencies = new ArrayList<>();

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

        public String getVariant() {
            return variant;
        }

        public void setVariant(String variant) {
            this.variant = variant;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
        }
    }
}
