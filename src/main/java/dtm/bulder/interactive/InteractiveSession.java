package dtm.bulder.interactive;

import dtm.bulder.cli.BuildContext;
import dtm.bulder.lifecycle.Phase;
import dtm.bulder.manifest.ProjectManifestFiles;
import dtm.bulder.printer.Printer;
import dtm.bulder.printer.Severity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public final class InteractiveSession {

    private final BuildContext context;
    private final Printer printer;

    public InteractiveSession(BuildContext context, Printer printer) {
        this.context = context;
        this.printer = printer;
    }

    public int run() {
        printBanner();

        ManifestWatcher watcher = null;
        try {
            watcher = new ManifestWatcher(context.projectPath(),
                    ProjectManifestFiles::isManifest,
                    this::onManifestChanged);
            watcher.start();
        } catch (IOException e) {
            printer.println(Severity.WARNING, "Watcher indisponivel: {}", e.getMessage());
        }

        context.printDiagnostics();
        context.refresh();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!handle(line.replace("\uFEFF", "").trim())) {
                    break;
                }
            }
        } catch (IOException e) {
            printer.println(Severity.ERROR, "Erro lendo stdin: {}", e.getMessage());
        } finally {
            if (watcher != null) {
                watcher.close();
            }
        }
        printer.println(Severity.INFO, "Sessao interativa encerrada");
        return 0;
    }

    private boolean handle(String command) {
        String lower = command.toLowerCase();
        switch (lower) {
            case "" -> {
                return true;
            }
            case "refresh" -> {
                context.refresh();
                return true;
            }
            case "reload" -> {
                context.printDiagnostics();
                return true;
            }
            case "status" -> {
                printer.println(Severity.INFO, "{}", context.describe());
                return true;
            }
            case "compile-commands", "clangd", "compdb" -> {
                printCompilationDatabase();
                return true;
            }
            case "help", "?" -> {
                printBanner();
                return true;
            }
            case "quit", "exit", "q" -> {
                return false;
            }
            default -> {
            }
        }
        return runPhaseLine(command, lower);
    }

    private boolean runPhaseLine(String command, String lower) {
        EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        for (String token : lower.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            Phase phase = Phase.fromString(token);
            if (phase == null) {
                printer.println(Severity.WARNING, "Comando desconhecido: {}", command);
                return true;
            }
            phases.add(phase);
        }
        if (!phases.isEmpty()) {
            // A ordem de execucao segue o lifecycle (clean -> build -> test -> install),
            // independente da ordem digitada.
            context.runPhases(phases);
        }
        return true;
    }

    private void onManifestChanged() {
        printer.println(Severity.INFO, "Manifest alterado; reprocessando...");
        context.printDiagnostics();
        context.refresh();
    }

    private void printCompilationDatabase() {
        try {
            printer.printlnRaw(context.compilationDatabaseJson());
        } catch (RuntimeException e) {
            printer.println(Severity.ERROR, "Falha ao gerar compile_commands.json: {}",
                    e.getMessage());
        }
    }

    private void printBanner() {
        printer.println(Severity.INFO,
                "Modo interativo. Fases (combinaveis, ordenadas pelo lifecycle): clean, build, "
                        + "test, install. Ex: 'install build clean' roda clean -> build -> install.");
        printer.println(Severity.INFO,
                "Outros comandos: refresh, reload, status, compile-commands, help, quit");
    }
}
