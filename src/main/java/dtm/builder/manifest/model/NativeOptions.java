package dtm.builder.manifest.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import dtm.builder.manifest.ManifestMerge;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class NativeOptions {
    private String icon;
    private boolean iconDeclared;
    private Map<String, String> metadata = new LinkedHashMap<>();
    public String getIcon() { return icon; }
    public void setIcon(String value) { icon = value; iconDeclared = true; }
    @JsonIgnore
    public boolean isIconDeclared() { return iconDeclared; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> value) {
        metadata = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
    private String asmCompiler;
    public String getAsmCompiler() { return asmCompiler; }
    public void setAsmCompiler(String value) { asmCompiler = value; }
    private String asmKind;
    public String getAsmKind() { return asmKind; }
    public void setAsmKind(String value) { asmKind = value; }
    private String linker;
    public String getLinker() { return linker; }
    public void setLinker(String value) { linker = value; }
    private String linkerKind;
    public String getLinkerKind() { return linkerKind; }
    public void setLinkerKind(String value) { linkerKind = value; }
    private String archiver;
    public String getArchiver() { return archiver; }
    public void setArchiver(String value) { archiver = value; }
    private String objcopy;
    public String getObjcopy() { return objcopy; }
    public void setObjcopy(String value) { objcopy = value; }
    private String asmFormat;
    public String getAsmFormat() { return asmFormat; }
    public void setAsmFormat(String value) { asmFormat = value; }
    private String outputName;
    public String getOutputName() { return outputName; }
    public void setOutputName(String value) { outputName = value; }
    private String linkMode;
    public String getLinkMode() { return linkMode; }
    public void setLinkMode(String value) { linkMode = value; }
    @JacksonXmlElementWrapper(localName = "asmFlags")
    @JacksonXmlProperty(localName = "asmFlag")
    private List<String> asmFlags = new ArrayList<>();
    public List<String> getAsmFlags() { return asmFlags; }
    public void setAsmFlags(List<String> value) { asmFlags = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    @JacksonXmlElementWrapper(localName = "linkDependencies")
    @JacksonXmlProperty(localName = "linkDependency")
    private List<String> linkDependencies = new ArrayList<>();
    public List<String> getLinkDependencies() { return linkDependencies; }
    public void setLinkDependencies(List<String> value) { linkDependencies = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public static void merge(NativeOptions base, NativeOptions override, NativeOptions out) {
        if (base == null) base = new NativeOptions();
        if (override == null) override = new NativeOptions();
        if (override.isIconDeclared()) out.setIcon(override.getIcon());
        else if (base.isIconDeclared()) out.setIcon(base.getIcon());
        Map<String, String> mergedMetadata = new LinkedHashMap<>(base.getMetadata());
        mergedMetadata.putAll(override.getMetadata());
        out.setMetadata(mergedMetadata);
        out.setAsmCompiler(ManifestMerge.pick(base.getAsmCompiler(), override.getAsmCompiler()));
        out.setAsmKind(ManifestMerge.pick(base.getAsmKind(), override.getAsmKind()));
        out.setLinker(ManifestMerge.pick(base.getLinker(), override.getLinker()));
        out.setLinkerKind(ManifestMerge.pick(base.getLinkerKind(), override.getLinkerKind()));
        out.setArchiver(ManifestMerge.pick(base.getArchiver(), override.getArchiver()));
        out.setObjcopy(ManifestMerge.pick(base.getObjcopy(), override.getObjcopy()));
        out.setAsmFormat(ManifestMerge.pick(base.getAsmFormat(), override.getAsmFormat()));
        out.setOutputName(ManifestMerge.pick(base.getOutputName(), override.getOutputName()));
        out.setLinkMode(ManifestMerge.pick(base.getLinkMode(), override.getLinkMode()));
        out.setAsmFlags(ManifestMerge.concat(base.getAsmFlags(), override.getAsmFlags()));
        out.setLinkDependencies(ManifestMerge.concat(base.getLinkDependencies(), override.getLinkDependencies()));
    }
}
