package dtm.builder;

import dtm.builder.cli.CommandDispatcher;

public class Main {

    public static void main(String[] args) {
        UserArgs userArgs = new UserArgs(args);
        int code;
        try {
            code = new CommandDispatcher().run(userArgs);
        } catch (Throwable t) {
            System.err.println("[BuildGraph] Erro fatal: " + describe(t));
            code = 1;
        }

        System.exit(code);
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message != null && !message.isBlank() ? message : t.getClass().getSimpleName();
    }
}
