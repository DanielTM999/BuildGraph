package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestTargetModel {

    public static final String TYPE_EXECUTABLE = "executable";
    public static final String TYPE_SHARED = "shared";
    public static final String TYPE_STATIC = "static";

    private String id;
    private String name;
    private String type;

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> sourceFolders = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeSourceFolders = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> includePaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeIncludePaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> defines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeDefines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> compileFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> linkFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> libraryPaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> dependsOn = new ArrayList<>();

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getSourceFolders() {
        return sourceFolders;
    }

    public void setSourceFolders(List<String> sourceFolders) {
        this.sourceFolders = sourceFolders != null ? sourceFolders : new ArrayList<>();
    }

    public List<String> getExcludeSourceFolders() {
        return excludeSourceFolders;
    }

    public void setExcludeSourceFolders(List<String> excludeSourceFolders) {
        this.excludeSourceFolders = excludeSourceFolders != null ? excludeSourceFolders : new ArrayList<>();
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths != null ? includePaths : new ArrayList<>();
    }

    public List<String> getExcludeIncludePaths() {
        return excludeIncludePaths;
    }

    public void setExcludeIncludePaths(List<String> excludeIncludePaths) {
        this.excludeIncludePaths = excludeIncludePaths != null ? excludeIncludePaths : new ArrayList<>();
    }

    public List<String> getDefines() {
        return defines;
    }

    public void setDefines(List<String> defines) {
        this.defines = defines != null ? defines : new ArrayList<>();
    }

    public List<String> getExcludeDefines() {
        return excludeDefines;
    }

    public void setExcludeDefines(List<String> excludeDefines) {
        this.excludeDefines = excludeDefines != null ? excludeDefines : new ArrayList<>();
    }

    public List<String> getCompileFlags() {
        return compileFlags;
    }

    public void setCompileFlags(List<String> compileFlags) {
        this.compileFlags = compileFlags != null ? compileFlags : new ArrayList<>();
    }

    public List<String> getLinkFlags() {
        return linkFlags;
    }

    public void setLinkFlags(List<String> linkFlags) {
        this.linkFlags = linkFlags != null ? linkFlags : new ArrayList<>();
    }

    public List<String> getLibraryPaths() {
        return libraryPaths;
    }

    public void setLibraryPaths(List<String> libraryPaths) {
        this.libraryPaths = libraryPaths != null ? libraryPaths : new ArrayList<>();
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
    }
}
