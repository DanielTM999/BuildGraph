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
                if (!handle(line.trim())) {
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
        switch (command.toLowerCase()) {
            case "" -> {
                return true;
            }
            case "build" -> context.runPhases(EnumSet.of(Phase.BUILD));
            case "clean" -> context.runPhases(EnumSet.of(Phase.CLEAN));
            case "test" -> context.runPhases(EnumSet.of(Phase.TEST));
            case "install" -> context.runPhases(EnumSet.of(Phase.INSTALL));
            case "refresh" -> context.refresh();
            case "reload" -> context.printDiagnostics();
            case "status" -> printer.println(Severity.INFO, "{}", context.describe());
            case "help", "?" -> printBanner();
            case "quit", "exit", "q" -> {
                return false;
            }
            default -> printer.println(Severity.WARNING, "Comando desconhecido: {}", command);
        }
        return true;
    }

    private void onManifestChanged() {
        printer.println(Severity.INFO, "Manifest alterado; reprocessando...");
        context.printDiagnostics();
        context.refresh();
    }

    private void printBanner() {
        printer.println(Severity.INFO,
                "Modo interativo. Comandos: build, clean, test, install, refresh, reload, status, quit");
    }
}
