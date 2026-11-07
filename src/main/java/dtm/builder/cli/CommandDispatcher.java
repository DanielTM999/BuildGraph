package dtm.builder.cli;

import dtm.builder.UserArgs;
import dtm.builder.interactive.InteractiveSession;
import dtm.builder.lifecycle.Phase;
import dtm.builder.printer.FormaterType;
import dtm.builder.printer.Printer;
import dtm.builder.printer.Severity;
import dtm.builder.printer.exceptions.FormaterTypeException;
import dtm.builder.repo.SyncResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;

public final class CommandDispatcher {

    private final Printer printer = Printer.getInstance();

    public int run(UserArgs args) {
        if (!applyFormat(args)) {
            return 2;
        }
        
        if(args.hasVersion()){
            printVersion();
            return 0;
        }

        if (args.hasHelp()) {
            printHelp();
            return 0;
        }

        if (args.isInvalidCommand()) {
            printer.println(Severity.ERROR, "Comando invalido: {}", args.getInvalidReason());
            printer.println(Severity.INFO, "Use --help para ver o uso");
            return 2;
        }

        Path projectPath = Paths.get(args.getProjectPath()).toAbsolutePath().normalize();
        if (!Files.exists(projectPath)) {
            printer.println(Severity.ERROR, "Diretorio do projeto nao existe: {}", projectPath);
            return 2;
        }
        if (!Files.isDirectory(projectPath)) {
            printer.println(Severity.ERROR, "Caminho do projeto nao e um diretorio: {}", projectPath);
            return 2;
        }

        BuildContext context = new BuildContext(projectPath, args.getRepoPath(),
                args.getProfile(), args.getCompiler(), args.getPackagesDir(), printer,
                args.getJobs(), args.getTargets(), args.getTestMain(),
                !args.hasNoIncremental(), args.getCCompiler(), args.getCxxCompiler(),
                args.hasSkipTests(), args.getSkippedTests(), args.getBuildOptions());

        if (args.hasCompileCommands()) {
            try {
                System.out.println(context.compilationDatabaseJson());
                return 0;
            } catch (RuntimeException e) {
                System.err.println("Falha ao gerar compile_commands.json: " + e.getMessage());
                return 1;
            }
        }

        printer.println(Severity.INFO, "Scanning for projects...");

        if (args.isInteractive()) {
            return new InteractiveSession(context, printer).run();
        }

        Set<Phase> phases = collectPhases(args);

        if (args.hasLock()) {
            SyncResult result = context.lock();
            if (result.isFailure()) {
                return 1;
            }
        }

        if (args.hasRefresh()) {
            SyncResult result = context.refresh();
            if (result.isFailure()) {
                return 1;
            }
        }

        if (phases.isEmpty()) {
            if (args.hasLock() || args.hasRefresh()) {
                return 0;
            }
            phases = EnumSet.of(Phase.BUILD);
        }

        boolean ok = context.runPhases(phases);
        return ok ? 0 : 1;
    }

    private boolean applyFormat(UserArgs args) {
        if (!args.hasFormat() || args.getFormat().isBlank()) {
            printer.setFormaterType(FormaterType.RAW);
            return true;
        }
        try {
            printer.setFormaterType(FormaterType.ofString(args.getFormat()));
            return true;
        } catch (FormaterTypeException e) {
            printer.println(Severity.ERROR, "Formato invalido: {}", e.getFormat());
            return false;
        }
    }

    private Set<Phase> collectPhases(UserArgs args) {
        Set<Phase> phases = EnumSet.noneOf(Phase.class);
        if (args.hasClean()) {
            phases.add(Phase.CLEAN);
        }
        if (args.hasBuild()) {
            phases.add(Phase.BUILD);
        }
        if (args.hasTest()) {
            phases.add(Phase.TEST);
        }
        if (args.hasInstall()) {
            phases.add(Phase.INSTALL);
        }
        return phases;
    }
    
    private void printVersion() {
        String version = CommandDispatcher.class
                .getPackage()
                .getImplementationVersion();
    
        if (version == null || version.isBlank()) {
            version = "dev";
        }
    
        printer.println(Severity.NONE, "BuildGraph {}", version);
    
        try (var stream = CommandDispatcher.class
                .getResourceAsStream("/META-INF/MANIFEST.MF")) {
    
            if (stream == null) {
                return;
            }
    
            var manifest = new java.util.jar.Manifest(stream);
            var attributes = manifest.getMainAttributes();
    
            String buildTime = attributes.getValue("Build-Time");
            String buildJdk = attributes.getValue("Build-Jdk");
            String copyright = attributes.getValue("Build-Copyright");
            String distributor = attributes.getValue("Build-Distributor");
            String license = attributes.getValue("Build-License");
    
            if (copyright != null) {
                printer.println(Severity.NONE, copyright);
            }
    
            if (distributor != null) {
                printer.println(Severity.NONE, "Distributed by: {}", distributor);
            }
    
            if (license != null) {
                printer.println(Severity.NONE, "License: {}", license);
            }
    
            if (buildTime != null) {
                var instant = java.time.Instant.parse(buildTime);
    
                var date = instant
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd HH:mm:ss z"));
    
                printer.println(Severity.NONE, "Build date: {}", date);
            }
    
            if (buildJdk != null) {
                printer.println(Severity.NONE, "Java: {}", buildJdk);
            }
    
        } catch (Exception e) {
            printer.println(Severity.NONE, "Build metadata unavailable");
        }
    }

    private void printHelp() {
        printer.println(Severity.NONE, "BuildGraph - mini build system para C/C++/ASM");
        printer.println(Severity.NONE, "");
        printer.println(Severity.NONE,
                "Uso: buildgraph [projectPath] [clean] [build] [install] [test] [refresh] [lock]");
        printer.println(Severity.NONE,
                "                [--interactive] [-f <fmt>] [-p <profile>] [-c <compiler>]");
        printer.println(Severity.NONE,
                "                [--repo <path>] [--packages <dir>] [--test-main <file>]");
        printer.println(Severity.NONE,
                "                [--compile-commands] [-t <target>] [-j <jobs>]");
        printer.println(Severity.NONE,
                "                [--no-incremental] [--skip-tests] [--skip-test <file>]");
        printer.println(Severity.NONE, "");
        printer.println(Severity.NONE, "  projectPath   diretorio do projeto (default: diretorio atual)");
        printer.println(Severity.NONE, "  clean/build/package/install/test  fases do lifecycle (default: build; package = build)");
        printer.println(Severity.NONE,
                "  refresh       resolve, atualiza lock e materializa dependencias");
        printer.println(Severity.NONE,
                "  lock          resolve e grava BuildGraph.lock.json sem materializar");
        printer.println(Severity.NONE, "  --interactive monitora o Manifest e aceita comandos via stdin");
        printer.println(Severity.NONE, "  -f, --format  raw | json | xml");
        printer.println(Severity.NONE, "  -p, --profile profile ativo (fallback do manifest)");
        printer.println(Severity.NONE, "  -c, --compiler compilador C/C++ (fallback do manifest)");
        printer.println(Severity.NONE, "  --c-compiler compilador C (fallback do manifest, precede --compiler)");
        printer.println(Severity.NONE, "  --cxx-compiler compilador C++ (fallback do manifest, precede --compiler)");
        printer.println(Severity.NONE, "  --test-main   arquivo com main() (fallback de testMain)");
        printer.println(Severity.NONE, "  --skip-tests  ignora todos os testes (soma a skipTests)");
        printer.println(Severity.NONE, "  --skip-test   ignora arquivo, pasta ou glob de testFolder (soma a excludeTests);");
        printer.println(Severity.NONE, "                repetivel ou separado por virgula; ctest -E / meson --no-suite");
        printer.println(Severity.NONE, "  --repo/--external  repo adicional; default final: ~/.buildgraph/repository");
        printer.println(Severity.NONE, "  --packages/--out   pasta local (fallback de packagesBase)");
        printer.println(Severity.NONE, "  --compile-commands/--clangd  imprime compile_commands.json no stdout");
        printer.println(Severity.NONE, "  -t, --target  limita o lifecycle ao target indicado e suas dependencias;");
        printer.println(Severity.NONE, "                repetivel ou separado por virgula");
        printer.println(Severity.NONE, "  -j, --jobs    auto ou limite global de compilacoes/links (1 = serial)");
        printer.println(Severity.NONE, "  --memory-limit <8G>     teto de RAM para processos de build");
        printer.println(Severity.NONE, "  --memory-per-job <768M> estimativa base por compilacao");
        printer.println(Severity.NONE, "  --memory-reserve <512M> reserva de RAM para o sistema (padrao automatico)");
        printer.println(Severity.NONE, "  --cache-dir <path> --cache-size <5G> --no-cache");
        printer.println(Severity.NONE, "  --profile-build        imprime tempos e contadores de performance");
        printer.println(Severity.NONE,
                "  build         incremental por padrao em builds diretos");
        printer.println(Severity.NONE,
                "  --no-incremental  ignora o estado nesta execucao/sessao");
        printer.println(Severity.NONE, "  outputDir default  <projeto>/build");
        printer.println(Severity.NONE, "  precedencia        manifest/profile -> CLI -> defaults");
        printer.println(Severity.NONE, "  install       publica o projeto (id:version) no repo global");
    }
}
