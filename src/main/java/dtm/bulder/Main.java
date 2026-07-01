package dtm.bulder;

import dtm.bulder.cli.CommandDispatcher;

public class Main {

    public static void main(String[] args) {
        UserArgs userArgs = new UserArgs(args);
        int code = new CommandDispatcher().run(userArgs);

        System.exit(code);
    }
}
