package dtm.builder.manifest.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Manifest")
public class ManifestRootModel {

    private String id;
    private String name;
    private String version;
    private String description;

    private String compilerVersion;
    private String cStandard;
    private String cxxStandard;
    private String toolchainVersion;
    private String platform;
    private String cCompiler;
    private String cxxCompiler;
    private String sysroot;
    private boolean library;
    private String outputDir;
    private String packagesBase;
    private String activeProfile;
    private String testFolder;
    private String testMain;

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> repositories = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> sources = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> includes = new ArrayList<>();

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
    private Map<String, String> env = new LinkedHashMap<>();

    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, String> properties = new LinkedHashMap<>();

    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, ManifestProfileModel> profiles = new LinkedHashMap<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ManifestPackagesModel> packages = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(localName = "tasks")
    @JacksonXmlProperty(localName = "task")
    private List<ManifestTaskModel> tasks = new ArrayList<>();

    @JsonSetter(nulls = Nulls.SKIP)
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ManifestTargetModel> targets = new ArrayList<>();

    @JsonIgnore
    private boolean packagesDeclared;

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

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    public void setToolchainVersion(String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
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

    public String getSysroot() {
        return sysroot;
    }

    public void setSysroot(String sysroot) {
        this.sysroot = sysroot;
    }

    public boolean isLibrary() {
        return library;
    }

    public void setLibrary(boolean library) {
        this.library = library;
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

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }

    public String getTestFolder() {
        return testFolder;
    }

    public void setTestFolder(String testFolder) {
        this.testFolder = testFolder;
    }

    public String getTestMain() {
        return testMain;
    }

    public void setTestMain(String testMain) {
        this.testMain = testMain;
    }

    public List<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories != null ? repositories : new ArrayList<>();
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes != null ? includes : new ArrayList<>();
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

    public Map<String, ManifestProfileModel> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, ManifestProfileModel> profiles) {
        this.profiles = profiles != null ? profiles : new LinkedHashMap<>();
    }

    public List<ManifestPackagesModel> getPackages() {
        return packages;
    }

    public void setPackages(List<ManifestPackagesModel> packages) {
        this.packages = packages != null ? packages : new ArrayList<>();
    }

    public List<ManifestTaskModel> getTasks() {
        return tasks;
    }

    public void setTasks(List<ManifestTaskModel> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
    }

    public List<ManifestTargetModel> getTargets() {
        return targets;
    }

    public void setTargets(List<ManifestTargetModel> targets) {
        this.targets = targets != null ? targets : new ArrayList<>();
    }

    public boolean isPackagesDeclared() {
        return packagesDeclared;
    }

    public void setPackagesDeclared(boolean packagesDeclared) {
        this.packagesDeclared = packagesDeclared;
    }
}
