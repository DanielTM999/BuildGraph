package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestPackagesModel {

    private String id;
    private String version;
    private String versionConstraint;
    private String downloadUrl;
    private boolean transitive = true;

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

    public String getVersionConstraint() {
        return versionConstraint;
    }

    public void setVersionConstraint(String versionConstraint) {
        this.versionConstraint = versionConstraint;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public void setTransitive(boolean transitive) {
        this.transitive = transitive;
    }

    public boolean hasDownloadUrl() {
        return downloadUrl != null && !downloadUrl.isBlank();
    }

    public String key() {
        String selector = version != null && !version.isBlank() ? version : versionConstraint;
        return (id == null ? "" : id) + ":" + (selector == null ? "" : selector);
    }
}
