package dtm.builder.build.resources;

import dtm.builder.build.BuildRequest;
import dtm.builder.build.TargetPlatform;
import dtm.builder.build.Toolchain;
import dtm.builder.manifest.model.ManifestRootModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

final class MacOSResourceProvider implements ExecutableResourceProvider {
    public Path tool(TargetPlatform platform, Toolchain compiler, boolean msvcLink, Path project) { return null; }
    public Output output(Path dir, boolean icon, boolean msvcLink, boolean driverLink) {
        Path plist = dir.resolve("Info.plist");
        List<Path> files = new ArrayList<>(List.of(plist));
        List<String> flags = new ArrayList<>();
        addSection(flags, driverLink, "__TEXT", "__info_plist", plist);
        if (icon) {
            Path icns = dir.resolve("icon.icns");
            files.add(icns);
            addSection(flags, driverLink, "__DATA", "__buildgraph_icon", icns);
        }
        return new Output(files, List.of(), flags);
    }
    private static void addSection(List<String> flags, boolean driver, String segment, String section, Path file) {
        if (driver) flags.add("-Wl,-sectcreate," + segment + "," + section + "," + file.toAbsolutePath());
        else flags.addAll(List.of("-sectcreate", segment, section, file.toAbsolutePath().toString()));
    }
    public void generate(BuildRequest req, ManifestRootModel manifest, TargetPlatform platform,
                         Toolchain compiler, Path tool, Path icon, Map<String, String> metadata,
                         Path dir, Output output, Consumer<String> log) throws IOException {
        StringBuilder plist = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\"><dict>\n");
        Map<String, String> known = Map.ofEntries(Map.entry("productName", "CFBundleName"),
                Map.entry("description", "CFBundleGetInfoString"), Map.entry("fileVersion", "CFBundleVersion"),
                Map.entry("productVersion", "CFBundleShortVersionString"), Map.entry("identifier", "CFBundleIdentifier"),
                Map.entry("copyright", "NSHumanReadableCopyright"));
        for (Map.Entry<String, String> entry : new TreeMap<>(metadata).entrySet()) {
            String key = known.getOrDefault(entry.getKey(), "BuildGraph." + entry.getKey());
            plist.append("<key>").append(xml(key)).append("</key><string>").append(xml(entry.getValue()))
                    .append("</string>\n");
        }
        if (icon != null) {
            plist.append("<key>CFBundleIconFile</key><string>icon.icns</string>\n");
            ExecutableResourceBuilder.write(dir.resolve("icon.icns"), IconEncoders.icns(IconConverter.read(icon)));
        }
        ExecutableResourceBuilder.write(dir.resolve("Info.plist"), plist.append("</dict></plist>\n").toString());
    }
    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
