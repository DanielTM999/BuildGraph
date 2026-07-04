package dtm.builder.manifest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import dtm.builder.manifest.model.ManifestDiagnostic;
import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ManifestParseResult;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTargetModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ManifestParser {

    private static final Set<String> KNOWN_COMPILER_VERSIONS = Set.of(
            "c89", "c90", "c99", "c11", "c17", "c18", "c23",
            "cpp98", "cpp03", "cpp11", "cpp14", "cpp17", "cpp20", "cpp23", "cpp26",
            "c++98", "c++03", "c++11", "c++14", "c++17", "c++20", "c++23", "c++26",
            "gnu89", "gnu90", "gnu99", "gnu11", "gnu17", "gnu18", "gnu23",
            "gnu++98", "gnu++03", "gnu++11", "gnu++14", "gnu++17", "gnu++20", "gnu++23", "gnu++26");

    private static final ObjectMapper JSON = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            .build();

    private static final XmlMapper XML = (XmlMapper) XmlMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            .build();

    private ManifestParser() {
    }

    public static ManifestParseResult readManifest(Path manifestPath) {
        List<ManifestDiagnostic> diagnostics = new ArrayList<>();
        String content;
        try {
            content = Files.readString(manifestPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.add(ManifestDiagnostic.error("manifest.read-error",
                    "Falha ao ler o manifest: " + e.getMessage()));
            return new ManifestParseResult(null, diagnostics);
        }
        return readManifest(content, ProjectManifestFiles.isXml(manifestPath));
    }

    public static ManifestParseResult readManifest(String content, boolean xml) {
        List<ManifestDiagnostic> diagnostics = new ArrayList<>();
        ManifestRootModel model;
        try {
            ObjectMapper mapper = xml ? XML : JSON;
            model = mapper.readValue(content, ManifestRootModel.class);
        } catch (Exception e) {
            diagnostics.add(ManifestDiagnostic.error(
                    xml ? "manifest.invalid-xml" : "manifest.invalid-json",
                    "Manifest invalido: " + describe(e)));
            return new ManifestParseResult(null, diagnostics);
        }

        if (model == null) {
            diagnostics.add(ManifestDiagnostic.error("manifest.empty", "Manifest vazio"));
            return new ManifestParseResult(null, diagnostics);
        }

        model.setPackagesDeclared(!model.getPackages().isEmpty());
        validate(model, diagnostics);
        return new ManifestParseResult(model, diagnostics);
    }

    private static void validate(ManifestRootModel model, List<ManifestDiagnostic> diagnostics) {
        if (isBlank(model.getId()) && isBlank(model.getName())) {
            diagnostics.add(ManifestDiagnostic.warning("manifest.identity-missing",
                    "Manifest sem 'id' nem 'name' de projeto"));
        }
        if (isBlank(model.getVersion())) {
            diagnostics.add(ManifestDiagnostic.warning("manifest.version-missing",
                    "Manifest sem 'version' de projeto"));
        }

        String cv = model.getCompilerVersion();
        if (cv != null && !cv.isBlank()
                && !KNOWN_COMPILER_VERSIONS.contains(cv.trim().toLowerCase())) {
            diagnostics.add(ManifestDiagnostic.warning("manifest.unsupported-compiler-version",
                    "compilerVersion desconhecido: " + cv));
        }

        int idx = 0;
        for (ManifestPackagesModel pkg : model.getPackages()) {
            if (pkg == null) {
                continue;
            }
            if (isBlank(pkg.getId())) {
                diagnostics.add(ManifestDiagnostic.error("manifest.packages-id-missing",
                        "package[" + idx + "] sem 'id'"));
            }
            if (isBlank(pkg.getVersion()) && isBlank(pkg.getVersionConstraint())) {
                diagnostics.add(ManifestDiagnostic.error("manifest.packages-version-missing",
                        "package[" + idx + "] sem 'version' nem 'versionConstraint'"));
            }
            idx++;
        }

        validateTargets(model, diagnostics);

        String active = model.getActiveProfile();
        if (active != null && !active.isBlank()
                && ManifestProfiles.activeProfile(model) == null) {
            diagnostics.add(ManifestDiagnostic.warning("manifest.active-profile-unknown",
                    "activeProfile nao encontrado nos profiles: " + active));
        }
    }

    private static void validateTargets(ManifestRootModel model, List<ManifestDiagnostic> diagnostics) {
        if (model.getTargets().isEmpty()) {
            return;
        }
        if (model.isLibrary()) {
            diagnostics.add(ManifestDiagnostic.warning("manifest.library-with-targets",
                    "'library' e 'targets' declarados juntos; 'targets' prevalece"));
        }
        Set<String> seen = new LinkedHashSet<>();
        int idx = 0;
        for (ManifestTargetModel target : model.getTargets()) {
            if (target == null) {
                idx++;
                continue;
            }
            String id = target.getId();
            if (isBlank(id)) {
                diagnostics.add(ManifestDiagnostic.error("manifest.target-id-missing",
                        "target[" + idx + "] sem 'id'"));
            } else if (!seen.add(id.trim())) {
                diagnostics.add(ManifestDiagnostic.error("manifest.target-id-duplicate",
                        "target 'id' duplicado: " + id.trim()));
            }
            String type = target.getType();
            if (type != null && !type.isBlank() && !isKnownTargetType(type)) {
                diagnostics.add(ManifestDiagnostic.error("manifest.target-type-unknown",
                        "target[" + idx + "] com 'type' desconhecido: " + type
                                + " (use executable, shared ou static)"));
            }
            if (!isBlank(id)) {
                for (String dep : target.getDependsOn()) {
                    if (dep != null && dep.trim().equals(id.trim())) {
                        diagnostics.add(ManifestDiagnostic.error("manifest.target-self-dependency",
                                "target '" + id.trim() + "' depende de si mesmo"));
                    }
                }
            }
            idx++;
        }
    }

    private static boolean isKnownTargetType(String type) {
        String t = type.trim().toLowerCase();
        return ManifestTargetModel.TYPE_EXECUTABLE.equals(t)
                || ManifestTargetModel.TYPE_SHARED.equals(t)
                || ManifestTargetModel.TYPE_STATIC.equals(t);
    }

    public static String writeString(ManifestRootModel model, boolean xml) throws IOException {
        ObjectMapper mapper = xml ? XML : JSON;
        return mapper.writeValueAsString(model);
    }

    public static void write(ManifestRootModel model, Path path) throws IOException {
        String out = writeString(model, ProjectManifestFiles.isXml(path));
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }
}
