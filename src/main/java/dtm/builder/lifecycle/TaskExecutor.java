package dtm.builder.lifecycle;

import dtm.builder.build.ProcessRunner;
import dtm.builder.manifest.model.ManifestRootModel;
import dtm.builder.manifest.model.ManifestTaskModel;
import dtm.builder.placeholder.PlaceholderResolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class TaskExecutor {

    private TaskExecutor() {
    }

    public static boolean runPhaseTasks(LifecycleContext ctx, Phase phase, String when) {
        List<ManifestTaskModel> selected = select(ctx.manifest(), phase, when);
        if (selected.isEmpty()) {
            return true;
        }

        List<ManifestTaskModel> ordered = order(selected, ctx.output());
        PlaceholderResolver resolver = ctx.placeholders();

        for (ManifestTaskModel task : ordered) {
            String command = resolver.resolve(task.getCommand());
            if (command == null || command.isBlank()) {
                ctx.output().accept("(task '" + task.identity() + "' sem command; ignorada)");
                continue;
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(resolver.resolveList(task.getArgs()));

            Path workingDir = resolveWorkingDir(ctx, resolver.resolve(task.getWorkingDir()));
            Map<String, String> env = buildEnv(ctx.manifest(), task, resolver);

            ctx.output().accept("[task " + task.identity() + "] + " + String.join(" ", cmd));
            int exit = ProcessRunner.run(cmd, workingDir, env, ctx.output());
            if (exit != 0 && task.isFailOnError()) {
                ctx.output().accept("Task '" + task.identity() + "' falhou (exit " + exit + ")");
                return false;
            }
        }
        return true;
    }

    private static List<ManifestTaskModel> select(ManifestRootModel manifest, Phase phase,
                                                  String when) {
        List<ManifestTaskModel> out = new ArrayList<>();
        if (manifest == null) {
            return out;
        }
        for (ManifestTaskModel task : manifest.getTasks()) {
            if (task == null || task.getPhase() == null) {
                continue;
            }
            if (!task.getPhase().trim().equalsIgnoreCase(phase.name())) {
                continue;
            }
            String taskWhen = task.getWhen() == null || task.getWhen().isBlank()
                    ? "before" : task.getWhen().trim();
            if (taskWhen.equalsIgnoreCase(when)) {
                out.add(task);
            }
        }
        return out;
    }

    private static Path resolveWorkingDir(LifecycleContext ctx, String workingDir) {
        if (workingDir == null || workingDir.isBlank()) {
            return ctx.projectPath();
        }
        Path p = Path.of(workingDir);
        return p.isAbsolute() ? p : ctx.projectPath().resolve(p).normalize();
    }

    private static Map<String, String> buildEnv(ManifestRootModel manifest, ManifestTaskModel task,
                                                PlaceholderResolver resolver) {
        Map<String, String> env = new LinkedHashMap<>();
        if (manifest != null) {
            env.putAll(resolver.resolveMap(manifest.getEnv()));
        }
        env.putAll(resolver.resolveMap(task.getEnv()));
        return env;
    }

    static List<ManifestTaskModel> order(List<ManifestTaskModel> tasks, Consumer<String> warn) {
        Map<String, ManifestTaskModel> byId = new LinkedHashMap<>();
        for (ManifestTaskModel t : tasks) {
            String id = t.identity();
            if (!id.isEmpty()) {
                byId.putIfAbsent(id, t);
            }
        }

        Map<ManifestTaskModel, Integer> indegree = new LinkedHashMap<>();
        Map<ManifestTaskModel, List<ManifestTaskModel>> edges = new LinkedHashMap<>();
        for (ManifestTaskModel t : tasks) {
            indegree.putIfAbsent(t, 0);
            edges.putIfAbsent(t, new ArrayList<>());
        }
        for (ManifestTaskModel t : tasks) {
            for (String dep : t.getDependsOn()) {
                ManifestTaskModel from = byId.get(dep);
                if (from != null && from != t) {
                    edges.get(from).add(t);
                    indegree.merge(t, 1, Integer::sum);
                }
            }
        }

        List<ManifestTaskModel> declOrder = new ArrayList<>(tasks);
        Comparator<ManifestTaskModel> tie = Comparator
                .comparingInt(ManifestTaskModel::getOrder)
                .thenComparingInt(declOrder::indexOf);

        List<ManifestTaskModel> result = new ArrayList<>();
        Set<ManifestTaskModel> available = new LinkedHashSet<>();
        for (ManifestTaskModel t : tasks) {
            if (indegree.get(t) == 0) {
                available.add(t);
            }
        }

        while (!available.isEmpty()) {
            ManifestTaskModel next = available.stream().min(tie).orElseThrow();
            available.remove(next);
            result.add(next);
            for (ManifestTaskModel to : edges.get(next)) {
                int d = indegree.merge(to, -1, Integer::sum);
                if (d == 0) {
                    available.add(to);
                }
            }
        }

        if (result.size() != tasks.size()) {
            if (warn != null) {
                warn.accept("Ciclo de dependencia entre tasks; usando ordem declarada");
            }
            List<ManifestTaskModel> fallback = new ArrayList<>(tasks);
            fallback.sort(tie);
            return fallback;
        }
        return result;
    }
}
