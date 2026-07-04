package dtm.builder.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ArchiverCommandBuilder {

    private ArchiverCommandBuilder() {
    }

    public static List<String> buildArchiveCommand(Path archiver, Path artifact,
                                                   List<Path> objects, boolean msvc) {
        List<String> cmd = new ArrayList<>();
        cmd.add(archiver.toString());
        if (msvc) {
            cmd.add("/nologo");
            cmd.add("/OUT:" + artifact);
        } else {
            cmd.add("rcs");
            cmd.add(artifact.toString());
        }
        for (Path obj : objects) {
            cmd.add(obj.toString());
        }
        return cmd;
    }
}
