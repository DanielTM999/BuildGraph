package dtm.builder;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UserArgs {

    public static final String PROJECT_PATH = "project_path";
    public static final String FORMAT = "format";
    public static final String PROFILE = "profile";
    public static final String COMPILER = "compiler";
    public static final String REPO = "repo";
    public static final String PACKAGES = "packages_dir";
    public static final String COMPILE_COMMANDS = "compile_commands";
    public static final String TARGETS = "targets";
    public static final String JOBS = "jobs";
    public static final String TEST_MAIN = "test_main";

    public static final String HAS_HELP = "has_help";
    public static final String INVALID_COMMAND = "invalid_command";
    public static final String INVALID_REASON = "invalid_reason";

    public static final String CLEAN = "clean";
    public static final String BUILD = "build";
    public static final String INSTALL = "install";
    public static final String TEST = "test";
    public static final String REFRESH = "refresh";
    public static final String LOCK = "lock";
    public static final String INTERACTIVE = "interactive";
    public static final String NO_INCREMENTAL = "no_incremental";

    private static final Set<String> RESERVED = Set.of(CLEAN, BUILD, INSTALL, TEST, REFRESH, LOCK);

    private final Map<String, String> argsMap = new ConcurrentHashMap<>();

    public UserArgs(String[] args) {
        parse(args);
    }

    public String getProjectPath() {
        return argsMap.getOrDefault(PROJECT_PATH, System.getProperty("user.dir", ""));
    }

    public boolean hasProjectPath() {
        return argsMap.containsKey(PROJECT_PATH);
    }

    public String getFormat() {
        return argsMap.getOrDefault(FORMAT, "");
    }

    public boolean hasFormat() {
        return argsMap.containsKey(FORMAT);
    }

    public String getProfile() {
        return argsMap.getOrDefault(PROFILE, "");
    }

    public boolean hasProfile() {
        return argsMap.containsKey(PROFILE);
    }

    public String getCompiler() {
        return argsMap.getOrDefault(COMPILER, "");
    }

    public boolean hasCompiler() {
        return argsMap.containsKey(COMPILER);
    }

    public String getTestMain() {
        return argsMap.getOrDefault(TEST_MAIN, "");
    }

    public boolean hasTestMain() {
        return argsMap.containsKey(TEST_MAIN);
    }

    public String getRepoPath() {
        return argsMap.getOrDefault(REPO, "");
    }

    public boolean hasRepoPath() {
        return argsMap.containsKey(REPO);
    }

    public String getPackagesDir() {
        return argsMap.getOrDefault(PACKAGES, "");
    }

    public boolean hasPackagesDir() {
        return argsMap.containsKey(PACKAGES);
    }

    public boolean hasHelp() {
        return flag(HAS_HELP);
    }

    public boolean isInvalidCommand() {
        return flag(INVALID_COMMAND);
    }

    public String getInvalidReason() {
        return argsMap.getOrDefault(INVALID_REASON, "");
    }

    public boolean hasClean() {
        return flag(CLEAN);
    }

    public boolean hasBuild() {
        return flag(BUILD);
    }

    public boolean hasInstall() {
        return flag(INSTALL);
    }

    public boolean hasTest() {
        return flag(TEST);
    }

    public boolean hasRefresh() {
        return flag(REFRESH);
    }

    public boolean hasLock() {
        return flag(LOCK);
    }

    public boolean isInteractive() {
        return flag(INTERACTIVE);
    }

    public boolean hasCompileCommands() {
        return flag(COMPILE_COMMANDS);
    }

    /** true desabilita o cache incremental (recompila tudo e regrava o estado). */
    public boolean hasNoIncremental() {
        return flag(NO_INCREMENTAL);
    }

    /** Ids passados via --target (repetível ou separados por vírgula). */
    public java.util.List<String> getTargets() {
        String raw = argsMap.getOrDefault(TARGETS, "");
        if (raw.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public boolean hasTargets() {
        return !getTargets().isEmpty();
    }

    /** 0 = default (paralelo com availableProcessors); 1 = serial. */
    public int getJobs() {
        String raw = argsMap.getOrDefault(JOBS, "");
        if (raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(argsMap);
    }

    private boolean flag(String key) {
        return Boolean.parseBoolean(argsMap.getOrDefault(key, "false"));
    }

    private void parse(String[] args) {
        argsMap.put(HAS_HELP, "false");
        argsMap.put(INVALID_COMMAND, "false");
        argsMap.put(CLEAN, "false");
        argsMap.put(BUILD, "false");
        argsMap.put(INSTALL, "false");
        argsMap.put(TEST, "false");
        argsMap.put(REFRESH, "false");
        argsMap.put(LOCK, "false");
        argsMap.put(INTERACTIVE, "false");
        argsMap.put(COMPILE_COMMANDS, "false");

        if (args == null || args.length == 0) {

            argsMap.put(BUILD, "true");
            return;
        }

        int index = 0;

        if (isHelp(args[0])) {
            argsMap.put(HAS_HELP, "true");
            if (args.length > 1) {
                invalid("O comando de ajuda nao aceita argumentos adicionais");
            }
            return;
        }

        String first = args[0];
        if (!first.startsWith("-") && !RESERVED.contains(first)) {
            argsMap.put(PROJECT_PATH, first);
            index = 1;
        }

        for (int i = index; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case CLEAN, BUILD, INSTALL, TEST, REFRESH, LOCK -> argsMap.put(arg, "true");
                case "--interactive", "-i" -> argsMap.put(INTERACTIVE, "true");
                case "--no-incremental" -> argsMap.put(NO_INCREMENTAL, "true");
                case "--compile-commands", "--clangd", "--compdb" ->
                        argsMap.put(COMPILE_COMMANDS, "true");
                case "-f", "-format", "--format" -> i = readValue(args, i, FORMAT);
                case "-p", "-profile", "--profile" -> i = readValue(args, i, PROFILE);
                case "-c", "-compiler", "--compiler" -> i = readValue(args, i, COMPILER);
                case "--test-main" -> {
                    i = readValue(args, i, TEST_MAIN);
                    if (argsMap.getOrDefault(TEST_MAIN, "").isBlank()) {
                        invalid("--test-main requer um arquivo");
                    }
                }
                case "--repo", "--external", "-repo" -> i = readValue(args, i, REPO);
                case "--packages", "--out", "-o" -> i = readValue(args, i, PACKAGES);
                case "--target", "-t", "--targets" -> i = appendValue(args, i, TARGETS);
                case "-j", "--jobs" -> {
                    i = readValue(args, i, JOBS);
                    String jobs = argsMap.getOrDefault(JOBS, "");
                    if (jobs.isBlank() || !jobs.trim().matches("\\d+")
                            || Integer.parseInt(jobs.trim()) < 1) {
                        invalid("--jobs requer um numero inteiro >= 1");
                    }
                }
                default -> invalid("Argumento desconhecido: " + arg);
            }
        }

        if (!hasClean() && !hasBuild() && !hasInstall() && !hasTest()
                && !hasRefresh() && !hasLock() && !isInteractive() && !hasCompileCommands()) {
            argsMap.put(BUILD, "true");
        }
    }

    private int appendValue(String[] args, int index, String key) {
        if (hasValue(args, index)) {
            String current = argsMap.getOrDefault(key, "");
            argsMap.put(key, current.isBlank() ? args[index + 1]
                    : current + "," + args[index + 1]);
            return index + 1;
        }
        invalid("--target requer o id de um target");
        return index;
    }

    private int readValue(String[] args, int index, String key) {
        if (hasValue(args, index)) {
            argsMap.put(key, args[index + 1]);
            return index + 1;
        }
        argsMap.put(key, "");
        return index;
    }

    private void invalid(String reason) {
        argsMap.put(INVALID_COMMAND, "true");
        argsMap.putIfAbsent(INVALID_REASON, reason);
    }

    private boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private boolean hasValue(String[] args, int index) {
        return index + 1 < args.length && !args[index + 1].startsWith("-");
    }
}
