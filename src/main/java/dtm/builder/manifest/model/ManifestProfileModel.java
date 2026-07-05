package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestProfileModel {

    private String buildType;
    private String platform;
    private String toolchainVersion;
    private String compilerVersion;
    private String cStandard;
    private String cxxStandard;
    private String sysroot;
    private String cCompiler;
    private String cxxCompiler;
    private String outputDir;
    private String packagesBase;
    private Boolean library;

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> repositories = new ArrayList<>();

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
    private List<String> compileFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> linkFlags = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> libraryPaths = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeIncludes = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeSources = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> excludeDefines = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ManifestPackagesModel> packages = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ManifestTargetModel> targets = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, String> env = new LinkedHashMap<>();

    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, String> properties = new LinkedHashMap<>();

    public String getBuildType() {
        return buildType;
    }

    public void setBuildType(String buildType) {
        this.buildType = buildType;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    public void setToolchainVersion(String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public void setCompilerVersion(String compilerVersion) {
        this.compilerVersion = compilerVersion;
    }

    public String getCStandard() {
        return cStandard;
    }

    public void setCStandard(String cStandard) {
        this.cStandard = cStandard;
    }

    public String getCxxStandard() {
        return cxxStandard;
    }

    public void setCxxStandard(String cxxStandard) {
        this.cxxStandard = cxxStandard;
    }

    public String getSysroot() {
        return sysroot;
    }

    public void setSysroot(String sysroot) {
        this.sysroot = sysroot;
    }

    public String getCCompiler() {
        return cCompiler;
    }

    public void setCCompiler(String cCompiler) {
        this.cCompiler = cCompiler;
    }

    public String getCxxCompiler() {
        return cxxCompiler;
    }

    public void setCxxCompiler(String cxxCompiler) {
        this.cxxCompiler = cxxCompiler;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getPackagesBase() {
        return packagesBase;
    }

    public void setPackagesBase(String packagesBase) {
        this.packagesBase = packagesBase;
    }

    public Boolean getLibrary() {
        return library;
    }

    public void setLibrary(Boolean library) {
        this.library = library;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories != null ? repositories : new ArrayList<>();
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

    public List<String> getExcludeIncludes() {
        return excludeIncludes;
    }

    public void setExcludeIncludes(List<String> excludeIncludes) {
        this.excludeIncludes = excludeIncludes != null ? excludeIncludes : new ArrayList<>();
    }

    public List<String> getExcludeSources() {
        return excludeSources;
    }

    public void setExcludeSources(List<String> excludeSources) {
        this.excludeSources = excludeSources != null ? excludeSources : new ArrayList<>();
    }

    public List<String> getExcludeDefines() {
        return excludeDefines;
    }

    public void setExcludeDefines(List<String> excludeDefines) {
        this.excludeDefines = excludeDefines != null ? excludeDefines : new ArrayList<>();
    }

    public List<ManifestPackagesModel> getPackages() {
        return packages;
    }

    public void setPackages(List<ManifestPackagesModel> packages) {
        this.packages = packages != null ? packages : new ArrayList<>();
    }

    public List<ManifestTargetModel> getTargets() {
        return targets;
    }

    public void setTargets(List<ManifestTargetModel> targets) {
        this.targets = targets != null ? targets : new ArrayList<>();
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env != null ? env : new LinkedHashMap<>();
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties != null ? properties : new LinkedHashMap<>();
    }
}
