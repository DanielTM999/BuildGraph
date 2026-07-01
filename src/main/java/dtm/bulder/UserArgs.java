package dtm.bulder;

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

    public static final String HAS_HELP = "has_help";
    public static final String INVALID_COMMAND = "invalid_command";
    public static final String INVALID_REASON = "invalid_reason";

    public static final String CLEAN = "clean";
    public static final String BUILD = "build";
    public static final String INSTALL = "install";
    public static final String TEST = "test";
    public static final String REFRESH = "refresh";
    public static final String INTERACTIVE = "interactive";

    private static final Set<String> RESERVED = Set.of(CLEAN, BUILD, INSTALL, TEST, REFRESH);

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

    public boolean isInteractive() {
        return flag(INTERACTIVE);
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
        argsMap.put(INTERACTIVE, "false");

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
                case CLEAN, BUILD, INSTALL, TEST, REFRESH -> argsMap.put(arg, "true");
                case "--interactive", "-i" -> argsMap.put(INTERACTIVE, "true");
                case "-f", "-format", "--format" -> i = readValue(args, i, FORMAT);
                case "-p", "-profile", "--profile" -> i = readValue(args, i, PROFILE);
                case "-c", "-compiler", "--compiler" -> i = readValue(args, i, COMPILER);
                case "--repo", "--external", "-repo" -> i = readValue(args, i, REPO);
                case "--packages", "--out", "-o" -> i = readValue(args, i, PACKAGES);
                default -> invalid("Argumento desconhecido: " + arg);
            }
        }

        if (!hasClean() && !hasBuild() && !hasInstall() && !hasTest()
                && !hasRefresh() && !isInteractive()) {
            argsMap.put(BUILD, "true");
        }
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
