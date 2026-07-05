package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

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
    @JacksonXmlElementWrapper(localName = "sources")
    @JacksonXmlProperty(localName = "source")
    private List<String> sources = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "excludeSources")
    @JacksonXmlProperty(localName = "excludeSource")
    private List<String> excludeSources = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "includes")
    @JacksonXmlProperty(localName = "include")
    private List<String> includes = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "excludeIncludes")
    @JacksonXmlProperty(localName = "excludeInclude")
    private List<String> excludeIncludes = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "defines")
    @JacksonXmlProperty(localName = "define")
    private List<String> defines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "excludeDefines")
    @JacksonXmlProperty(localName = "excludeDefine")
    private List<String> excludeDefines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "compileFlags")
    @JacksonXmlProperty(localName = "compileFlag")
    private List<String> compileFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "linkFlags")
    @JacksonXmlProperty(localName = "linkFlag")
    private List<String> linkFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "libraryPaths")
    @JacksonXmlProperty(localName = "libraryPath")
    private List<String> libraryPaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "dependsOn")
    @JacksonXmlProperty(localName = "dependency")
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

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public List<String> getExcludeSources() {
        return excludeSources;
    }

    public void setExcludeSources(List<String> excludeSources) {
        this.excludeSources = excludeSources != null ? excludeSources : new ArrayList<>();
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes != null ? includes : new ArrayList<>();
    }

    public List<String> getExcludeIncludes() {
        return excludeIncludes;
    }

    public void setExcludeIncludes(List<String> excludeIncludes) {
        this.excludeIncludes = excludeIncludes != null ? excludeIncludes : new ArrayList<>();
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
