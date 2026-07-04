package dtm.builder.placeholder;

import dtm.builder.manifest.model.ManifestProfileModel;
import dtm.builder.manifest.model.ManifestRootModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderResolver {

    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");
    private static final int MAX_PASSES = 10;

    private final PlaceholderContext context;
    private final Consumer<String> onUnresolved;

    public PlaceholderResolver(PlaceholderContext context) {
        this(context, null);
    }

    public PlaceholderResolver(PlaceholderContext context, Consumer<String> onUnresolved) {
        this.context = context;
        this.onUnresolved = onUnresolved;
    }

    public String resolve(String input) {
        if (input == null || input.indexOf("${") < 0) {
            return input;
        }
        String current = input;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            Matcher m = TOKEN.matcher(current);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            while (m.find()) {
                String key = m.group(1).trim();
                String value = lookup(key);
                if (value != null) {
                    changed = true;
                    m.appendReplacement(sb, Matcher.quoteReplacement(value));
                } else {
                    if (onUnresolved != null) {
                        onUnresolved.accept(key);
                    }
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            String next = sb.toString();
            if (!changed || next.equals(current)) {
                return next;
            }
            current = next;
        }
        return current;
    }

    public List<String> resolveList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>(values.size());
        for (String v : values) {
            out.add(resolve(v));
        }
        return out;
    }

    public Map<String, String> resolveMap(Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        if (values == null) {
            return out;
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            out.put(e.getKey(), resolve(e.getValue()));
        }
        return out;
    }

    private String lookup(String key) {
        ManifestRootModel manifest = context.manifest();

        switch (key) {
            case "user.dir":
                return System.getProperty("user.dir", "");
            case "user.home":
                return System.getProperty("user.home", "");
            case "project.dir":
                return context.projectDir() == null ? null : context.projectDir().toString();
            default:
                break;
        }

        if (manifest != null) {
            switch (key) {
                case "project.id":
                    return orNull(manifest.getId());
                case "project.name":
                    return orNull(manifest.getName());
                case "project.version":
                    return orNull(manifest.getVersion());
                case "project.description":
                    return orNull(manifest.getDescription());
                default:
                    break;
            }
        }

        if (key.startsWith("env.")) {
            return context.env().get(key.substring(4));
        }

        if (key.startsWith("properties.")) {
            return propertyValue(key.substring("properties.".length()));
        }

        if (key.startsWith("profile.")) {
            return profileValue(key.substring("profile.".length()));
        }

        return propertyValue(key);
    }

    private String profileValue(String rest) {
        int dot = rest.indexOf('.');
        if (dot < 0) {
            return null;
        }
        String selector = rest.substring(0, dot);
        String prop = rest.substring(dot + 1);
        ManifestRootModel manifest = context.manifest();
        if (manifest == null) {
            return null;
        }

        ManifestProfileModel profile;
        if ("current".equals(selector)) {
            String active = manifest.getActiveProfile();
            profile = active == null ? null : findProfile(manifest, active);
        } else {
            profile = findProfile(manifest, selector);
        }
        if (profile == null) {
            return null;
        }
        return profileProp(profile, prop);
    }

    private ManifestProfileModel findProfile(ManifestRootModel manifest, String name) {
        if (name == null) {
            return null;
        }
        String norm = name.trim().toLowerCase();
        for (Map.Entry<String, ManifestProfileModel> e : manifest.getProfiles().entrySet()) {
            if (e.getKey() != null && e.getKey().trim().toLowerCase().equals(norm)) {
                return e.getValue();
            }
        }
        return null;
    }

    private String profileProp(ManifestProfileModel profile, String prop) {
        String fromProps = profile.getProperties().get(prop);
        if (fromProps != null) {
            return fromProps;
        }
        String scalar = switch (prop) {
            case "buildType" -> profile.getBuildType();
            case "platform" -> profile.getPlatform();
            case "toolchainVersion" -> profile.getToolchainVersion();
            case "compilerVersion" -> profile.getCompilerVersion();
            case "cStandard" -> profile.getCStandard();
            case "cxxStandard" -> profile.getCxxStandard();
            case "sysroot" -> profile.getSysroot();
            case "cCompiler" -> profile.getCCompiler();
            case "cxxCompiler" -> profile.getCxxCompiler();
            case "outputDir" -> profile.getOutputDir();
            case "packagesBase" -> profile.getPackagesBase();
            case "library" -> profile.getLibrary() == null ? null : String.valueOf(profile.getLibrary());
            default -> null;
        };
        if (scalar != null) {
            return scalar;
        }

        return propertyValue(prop);
    }

    private String propertyValue(String key) {
        ManifestRootModel manifest = context.manifest();
        if (manifest == null) {
            return null;
        }
        return manifest.getProperties().get(key);
    }

    private static String orNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
