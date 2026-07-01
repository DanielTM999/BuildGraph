package dtm.bulder.cli;

import dtm.bulder.build.BuildDiagnosticParser;
import dtm.bulder.build.BuildSystem;
import dtm.bulder.build.BuildSystemDetector;
import dtm.bulder.build.Toolchain;
import dtm.bulder.build.ToolchainDetector;
import dtm.bulder.manifest.ManifestProfiles;
import dtm.bulder.manifest.ManifestResolver;
import dtm.bulder.manifest.ProjectManifestFiles;
import dtm.bulder.manifest.model.ManifestDiagnostic;
import dtm.bulder.manifest.model.ManifestParseResult;
import dtm.bulder.manifest.model.ManifestProfileModel;
import dtm.bulder.manifest.model.ManifestRootModel;
import dtm.bulder.lifecycle.LifecycleContext;
import dtm.bulder.lifecycle.LifecycleExecutor;
import dtm.bulder.lifecycle.LifecycleResult;
import dtm.bulder.lifecycle.Phase;
import dtm.bulder.placeholder.PlaceholderContext;
import dtm.bulder.placeholder.PlaceholderResolver;
import dtm.bulder.printer.Printer;
import dtm.bulder.printer.Severity;
import dtm.bulder.printer.formater.RawFormater;
import dtm.bulder.repo.GlobalRepository;
import dtm.bulder.repo.SyncResult;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

public final class BuildContext {

    private final Path projectPath;
    private final String repoOverride;
    private final String profileOverride;
    private final String compilerOverride;
    private final String packagesOverride;
    private final Printer printer;

    public BuildContext(Path projectPath, String repoOverride, String profileOverride,
                        String compilerOverride, String packagesOverride, Printer printer) {
        this.projectPath = ProjectManifestFiles.normalizeRoot(projectPath);
        this.repoOverride = repoOverride;
        this.profileOverride = profileOverride;
        this.compilerOverride = compilerOverride;
        this.packagesOverride = packagesOverride;
        this.printer = printer;
    }

    public Path projectPath() {
        return projectPath;
    }

    public void printDiagnostics() {
        ManifestParseResult parse = readManifest();
        if (manifestPath() == null) {
            printer.println(Severity.WARNING, "Nenhum Manifest.json/Manifest.xml encontrado em {}",
                    projectPath);
            return;
        }
        printDiagnostics(parse);
    }

    public SyncResult refresh() {
        Configuration configuration = configuration(readManifest());
        return doRefresh(configuration);
    }

    private SyncResult doRefresh(Configuration configuration) {
        printer.println(Severity.INFO, "Sincronizando dependencias...");
        SyncResult result = configuration.resolver().syncPackages(profileOverride,
                configuration.packagesDir(),
                msg -> printer.println(Severity.INFO, "{}", msg));
        Severity sev = result.isFailure() ? Severity.ERROR : Severity.INFO;
        printer.println(sev, "Refresh: {}", result.name());
        return result;
    }

    public boolean runPhases(Set<Phase> phases) {
        ManifestParseResult parse = readManifest();
        boolean hasManifest = manifestPath() != null;
        if (hasManifest) {
            printDiagnostics(parse);
            if (!parse.isOk()) {
                printer.println(Severity.ERROR, "Manifest invalido; abortando");
                return false;
            }
        }

        Configuration configuration = configuration(parse);
        ManifestRootModel raw = configuration.raw();
        ManifestProfileModel activeProfile = ManifestProfiles.activeProfile(raw);
        ManifestRootModel effective = configuration.effective();

        String buildMode = activeProfile != null && notBlank(activeProfile.getBuildType())
                ? activeProfile.getBuildType() : "Debug";

        BuildSystem buildSystem = BuildSystemDetector.detect(projectPath, hasManifest);
        Toolchain toolchain = ToolchainDetector.resolve(effective, compilerOverride);
        Path packagesDir = configuration.packagesDir();
        Path buildDir = ProjectManifestFiles.resolveBuildDir(projectPath,
                effective.getOutputDir());

        if (needsBuild(phases) && effective.isPackagesDeclared()) {
            SyncResult sync = doRefresh(configuration);
            if (sync.isFailure()) {
                printer.println(Severity.ERROR, "Falha ao resolver dependencias; abortando");
                return false;
            }
        }

        PlaceholderResolver placeholders = new PlaceholderResolver(
                new PlaceholderContext(projectPath, effective),
                key -> printer.println(Severity.WARNING, "Placeholder nao resolvido: ${{}}", key));

        LifecycleContext ctx = new LifecycleContext(projectPath, effective, toolchain, buildSystem,
                configuration.repository(), packagesDir, buildDir, buildMode, placeholders,
                buildOutput());

        LifecycleResult result = LifecycleExecutor.run(ctx, phases);

        printer.println(Severity.INFO, "------------------------------------------------");
        if (result.success()) {
            printer.println(Severity.INFO, "BUILD SUCCESS", RawFormater.TextColor.GREEN);
        } else {
            printer.println(Severity.ERROR, "BUILD FAILURE - {}", result.message(),
                    RawFormater.TextColor.RED);
        }
        printer.println(Severity.INFO, "------------------------------------------------");
        return result.success();
    }

    private Consumer<String> buildOutput() {
        return line -> {
            BuildDiagnosticParser.Level level = BuildDiagnosticParser.classify(line);
            Severity sev = switch (level) {
                case ERROR -> Severity.ERROR;
                case WARNING -> Severity.WARNING;
                case NOTE -> Severity.INFO;
                case PLAIN -> Severity.NONE;
            };
            printer.println(sev, "{}", line);
        };
    }

    public String describe() {
        Configuration configuration = configuration(readManifest());
        BuildSystem buildSystem = BuildSystemDetector.detect(projectPath,
                manifestPath() != null);
        Toolchain toolchain = ToolchainDetector.resolve(configuration.effective(), compilerOverride);
        return "project=" + projectPath
                + ", buildSystem=" + buildSystem
                + ", toolchain=" + (toolchain == null ? "<nenhuma>" : toolchain.displayName());
    }

    private Path manifestPath() {
        return ProjectManifestFiles.firstExistingProjectManifest(projectPath);
    }

    private ManifestParseResult readManifest() {
        Path path = manifestPath();
        return path == null
                ? new ManifestParseResult(null, java.util.List.of())
                : dtm.bulder.manifest.ManifestParser.readManifest(path);
    }

    private void printDiagnostics(ManifestParseResult parse) {
        for (ManifestDiagnostic d : parse.getDiagnostics()) {
            Severity sev = switch (d.getSeverity()) {
                case ERROR -> Severity.ERROR;
                case WARNING -> Severity.WARNING;
                case INFO -> Severity.INFO;
            };
            printer.println(sev, "{}: {}", d.getCode(), d.getMessage());
        }
    }

    private Configuration configuration(ManifestParseResult parse) {
        ManifestRootModel raw = parse.getManifest() != null
                ? parse.getManifest() : new ManifestRootModel();
        if (!notBlank(raw.getActiveProfile()) && notBlank(profileOverride)) {
            raw.setActiveProfile(profileOverride);
        }
        ManifestRootModel effective = ManifestProfiles.effective(raw);
        GlobalRepository repository = GlobalRepository.resolve(projectPath,
                effective.getRepositories(), repoOverride);
        ManifestResolver resolver = new ManifestResolver(projectPath, repository);
        Path packagesDir = ProjectManifestFiles.resolvePackagesDir(projectPath,
                effective.getPackagesBase(), packagesOverride);
        return new Configuration(raw, effective, repository, resolver, packagesDir);
    }

    private record Configuration(ManifestRootModel raw, ManifestRootModel effective,
                                 GlobalRepository repository, ManifestResolver resolver,
                                 Path packagesDir) {
    }

    private static boolean needsBuild(Set<Phase> phases) {
        return phases.contains(Phase.BUILD) || phases.contains(Phase.TEST)
                || phases.contains(Phase.INSTALL);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
