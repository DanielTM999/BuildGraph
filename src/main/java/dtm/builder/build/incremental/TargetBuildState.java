package dtm.builder.build.incremental;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetBuildState {

    public static final int SCHEMA_VERSION = 2;

    private int schemaVersion = SCHEMA_VERSION;
    private String toolchainFingerprint = "";
    private String buildMode = "";
    private String linkCommandHash = "";
    private Map<String, String> linkInputs = new LinkedHashMap<>();
    private Map<String, ObjectState> objects = new LinkedHashMap<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getToolchainFingerprint() {
        return toolchainFingerprint;
    }

    public void setToolchainFingerprint(String toolchainFingerprint) {
        this.toolchainFingerprint = toolchainFingerprint == null ? "" : toolchainFingerprint;
    }

    public String getBuildMode() {
        return buildMode;
    }

    public void setBuildMode(String buildMode) {
        this.buildMode = buildMode == null ? "" : buildMode;
    }

    public String getLinkCommandHash() {
        return linkCommandHash;
    }

    public void setLinkCommandHash(String linkCommandHash) {
        this.linkCommandHash = linkCommandHash == null ? "" : linkCommandHash;
    }

    public Map<String, String> getLinkInputs() {
        return linkInputs;
    }

    public void setLinkInputs(Map<String, String> linkInputs) {
        this.linkInputs = linkInputs == null ? new LinkedHashMap<>() : linkInputs;
    }

    public Map<String, ObjectState> getObjects() {
        return objects;
    }

    public void setObjects(Map<String, ObjectState> objects) {
        this.objects = objects == null ? new LinkedHashMap<>() : objects;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ObjectState {

        private String sourceStamp = "";
        private String commandHash = "";
        private String objectFile = "";
        private Map<String, String> headers = new LinkedHashMap<>();

        public String getSourceStamp() {
            return sourceStamp;
        }

        public void setSourceStamp(String sourceStamp) {
            this.sourceStamp = sourceStamp == null ? "" : sourceStamp;
        }

        public String getCommandHash() {
            return commandHash;
        }

        public void setCommandHash(String commandHash) {
            this.commandHash = commandHash == null ? "" : commandHash;
        }

        public String getObjectFile() {
            return objectFile;
        }

        public void setObjectFile(String objectFile) {
            this.objectFile = objectFile == null ? "" : objectFile;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
        }
    }
}
