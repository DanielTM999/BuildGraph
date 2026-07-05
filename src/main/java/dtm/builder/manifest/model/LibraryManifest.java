package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LibraryManifest {

    public static final String KIND_SOURCE = "source";
    public static final String KIND_PRECOMPILED = "precompiled";

    private String id;
    private String name;
    private String version;
    private String description;
    private String url;
    private String source;
    private String kind;
    private String platform;

    @JsonAlias("packages")
    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ManifestPackagesModel> dependencies = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> includes = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> sources = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> defines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> libraryPaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> linkLibraries = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> runtimeFiles = new ArrayList<>();

    public boolean isPrecompiled() {
        return KIND_PRECOMPILED.equalsIgnoreCase(kind);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes != null ? includes : new ArrayList<>();
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public List<String> getDefines() {
        return defines;
    }

    public void setDefines(List<String> defines) {
        this.defines = defines != null ? defines : new ArrayList<>();
    }

    public List<String> getLibraryPaths() {
        return libraryPaths;
    }

    public void setLibraryPaths(List<String> libraryPaths) {
        this.libraryPaths = libraryPaths != null ? libraryPaths : new ArrayList<>();
    }

    public List<String> getLinkLibraries() {
        return linkLibraries;
    }

    public void setLinkLibraries(List<String> linkLibraries) {
        this.linkLibraries = linkLibraries != null ? linkLibraries : new ArrayList<>();
    }

    public List<String> getRuntimeFiles() {
        return runtimeFiles;
    }

    public void setRuntimeFiles(List<String> runtimeFiles) {
        this.runtimeFiles = runtimeFiles != null ? runtimeFiles : new ArrayList<>();
    }

    public List<ManifestPackagesModel> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<ManifestPackagesModel> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }
}
