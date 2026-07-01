package dtm.bulder.lifecycle;

import dtm.bulder.manifest.model.ManifestTaskModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutorOrderTest {

    private ManifestTaskModel task(String id, int order, String... dependsOn) {
        ManifestTaskModel t = new ManifestTaskModel();
        t.setId(id);
        t.setOrder(order);
        t.setDependsOn(new java.util.ArrayList<>(List.of(dependsOn)));
        t.setCommand("echo");
        return t;
    }

    private List<String> ids(List<ManifestTaskModel> tasks) {
        return tasks.stream().map(ManifestTaskModel::getId).collect(Collectors.toList());
    }

    @Test
    void respectsDependsOnBeforeOrder() {

        List<ManifestTaskModel> tasks = List.of(
                task("b", 1, "a"),
                task("a", 5));
        List<ManifestTaskModel> ordered = TaskExecutor.order(tasks, null);
        assertEquals(List.of("a", "b"), ids(ordered));
    }

    @Test
    void breaksTiesByOrder() {
        List<ManifestTaskModel> tasks = List.of(
                task("c", 30),
                task("a", 10),
                task("b", 20));
        List<ManifestTaskModel> ordered = TaskExecutor.order(tasks, null);
        assertEquals(List.of("a", "b", "c"), ids(ordered));
    }

    @Test
    void cycleFallsBackAndKeepsAllTasks() {
        List<ManifestTaskModel> tasks = List.of(
                task("a", 1, "b"),
                task("b", 2, "a"));
        List<ManifestTaskModel> ordered = TaskExecutor.order(tasks, msg -> { });
        assertEquals(2, ordered.size());
        assertTrue(ids(ordered).containsAll(List.of("a", "b")));
    }
}
