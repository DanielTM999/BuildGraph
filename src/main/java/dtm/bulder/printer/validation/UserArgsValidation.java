package dtm.bulder.printer.validation;

import dtm.bulder.UserArgs;
import dtm.bulder.printer.exceptions.NoProjectPathException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UserArgsValidation {

    public static void valid(UserArgs userArgs) throws Exception {
        if(userArgs == null) throw new Exception("Sem argumentos");
        validProjectPath(userArgs);
    }

    private static void validProjectPath(UserArgs userArgs) throws Exception {
        if(!userArgs.hasProjectPath()){
            throw new NoProjectPathException("Project not defined");
        }

        Path path = Paths.get(userArgs.getProjectPath());

        if(Files.notExists(path)){

        }

        if(Files.isDirectory(path)){

        }else {

        }

    }
}
