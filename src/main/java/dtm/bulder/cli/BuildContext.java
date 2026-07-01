package dtm.bulder.cli;

import dtm.bulder.build.BuildDiagnosticParser;
import dtm.bulder.build.BuildSystem;
import dtm.bulder.build.BuildSystemDetector;
import dtm.bulder.build.Toolchain;
import dtm.bulder.build.ToolchainDetector;
import dtm.bulder.integration.CompilationDatabaseGenerator;
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
import java.nio.file.Files;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.function.Consumer;

public final class BuildContext {

    private static final int LINE_WIDTH = 72;
    private static final String SEPARATOR = "-".repeat(LINE_WIDTH);

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

    public String compilationDatabaseJson() {
        ManifestParseResult parse = readManifest();
        if (!parse.isOk()) {
            throw new IllegalStateException("Manifest invalido");
        }
        Configuration configuration = configuration(parse);
        ManifestRootModel raw = configuration.raw();
        ManifestRootModel effective = configuration.effective();
        Path buildDir = ProjectManifestFiles.resolveBuildDir(projectPath,
                effective.getOutputDir());
        BuildSystem buildSystem = BuildSystemDetector.detect(projectPath,
                manifestPath() != null);
        Path existing = buildDir.resolve("compile_commands.json");
        if (buildSystem != BuildSystem.MANIFEST && buildSystem != BuildSystem.DEFAULT
                && Files.isRegularFile(existing)) {
            try {
                return CompilationDatabaseGenerator.normalize(Files.readString(existing));
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler " + existing, e);
            }
        }
        if (effective.isPackagesDeclared()) {
            SyncResult sync = configuration.resolver().syncPackages(profileOverride,
                    configuration.packagesDir(), null);
            if (sync.isFailure()) {
                throw new IllegalStateException("Falha ao resolver dependencias: " + sync.name());
            }
        }
        ManifestProfileModel activeProfile = ManifestProfiles.activeProfile(raw);
        String buildMode = activeProfile != null && notBlank(activeProfile.getBuildType())
                ? activeProfile.getBuildType() : "Debug";
        Toolchain toolchain = ToolchainDetector.resolve(effective, compilerOverride);
        return CompilationDatabaseGenerator.generate(projectPath, effective, toolchain,
                configuration.packagesDir(), buildDir, buildMode);
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

        printProjectHeader(effective, hasManifest);
        long startNanos = System.nanoTime();

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
                buildOutput(), line -> printer.println(Severity.INFO, "{}", line));

        LifecycleResult result = LifecycleExecutor.run(ctx, phases);

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        printBuildSummary(result, elapsedMillis);
        return result.success();
    }

    private void printProjectHeader(ManifestRootModel manifest, boolean hasManifest) {
        String id = notBlank(manifest.getId()) ? manifest.getId() : manifest.getName();
        String name = notBlank(manifest.getName()) ? manifest.getName()
                : (notBlank(id) ? id : projectPath.getFileName().toString());
        String coordinate = notBlank(id) ? id
                : (hasManifest ? name : projectPath.getFileName().toString());
        String version = notBlank(manifest.getVersion()) ? manifest.getVersion() : null;
        String kind = manifest.isLibrary() ? "lib" : "exe";

        printer.println(Severity.INFO, "");
        printer.println(Severity.INFO, "{}", centered("< " + coordinate + " >"));
        printer.println(Severity.INFO, "Building {}", version == null ? name : name + " " + version);
        printer.println(Severity.INFO, "{}", centered("[ " + kind + " ]"));
    }

    private void printBuildSummary(LifecycleResult result, long elapsedMillis) {
        printer.println(Severity.INFO, "");
        printer.println(Severity.INFO, "{}", SEPARATOR);
        if (result.success()) {
            printer.println(Severity.INFO, "BUILD SUCCESS", RawFormater.TextColor.GREEN);
        } else {
            printer.println(Severity.ERROR, "BUILD FAILURE", RawFormater.TextColor.RED);
            printer.println(Severity.ERROR, "{}", result.message());
        }
        printer.println(Severity.INFO, "{}", SEPARATOR);
        printer.println(Severity.INFO, "Total time:  {}", formatDuration(elapsedMillis));
        printer.println(Severity.INFO, "Finished at: {}", ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")));
        printer.println(Severity.INFO, "{}", SEPARATOR);
    }

    private static String centered(String label) {
        int total = LINE_WIDTH - label.length();
        if (total < 2) {
            return label;
        }
        int left = total / 2;
        int right = total - left;
        return "-".repeat(left) + label + "-".repeat(right);
    }

    private static String formatDuration(long elapsedMillis) {
        if (elapsedMillis < 60_000L) {
            return String.format(java.util.Locale.ROOT, "%.3f s", elapsedMillis / 1000.0);
        }
        long totalSeconds = elapsedMillis / 1000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d min",
                totalSeconds / 60, totalSeconds % 60);
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
