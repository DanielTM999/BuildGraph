package dtm.builder.build.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grafo de dependências entre targets (arestas dependsOn). Fornece ordem
 * topológica (Kahn), detecção de ciclos, o conjunto de targets prontos dado
 * o que já foi concluído (para o scheduler paralelo) e o fechamento
 * transitivo de um subconjunto (para --target).
 */
public final class TargetGraph {

    private final Map<String, ResolvedTarget> byId = new LinkedHashMap<>();

    private TargetGraph(List<ResolvedTarget> targets) {
        for (ResolvedTarget t : targets) {
            byId.put(t.id(), t);
        }
    }

    public static TargetGraph of(List<ResolvedTarget> targets) {
        return new TargetGraph(targets == null ? List.of() : targets);
    }

    public int size() {
        return byId.size();
    }

    public ResolvedTarget target(String id) {
        return byId.get(id);
    }

    public Collection<ResolvedTarget> targets() {
        return byId.values();
    }

    /** Ids envolvidos em algum ciclo, vazio quando o grafo é acíclico. */
    public List<String> cycle() {
        Map<String, Integer> pending = pendingDependencies();
        Deque<String> queue = new ArrayDeque<>();
        pending.forEach((id, count) -> {
            if (count == 0) {
                queue.add(id);
            }
        });
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            processed++;
            for (ResolvedTarget t : byId.values()) {
                if (t.dependsOn().contains(id)) {
                    int left = pending.merge(t.id(), -1, Integer::sum);
                    if (left == 0) {
                        queue.add(t.id());
                    }
                }
            }
        }
        if (processed == byId.size()) {
            return List.of();
        }
        List<String> cyclic = new ArrayList<>();
        pending.forEach((id, count) -> {
            if (count > 0) {
                cyclic.add(id);
            }
        });
        return cyclic;
    }

    /** Ordem topológica estável (declaração desempata). Exige grafo acíclico. */
    public List<ResolvedTarget> topologicalOrder() {
        Map<String, Integer> pending = pendingDependencies();
        List<ResolvedTarget> out = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        while (out.size() < byId.size()) {
            boolean advanced = false;
            for (ResolvedTarget t : byId.values()) {
                if (emitted.contains(t.id()) || pending.get(t.id()) != 0) {
                    continue;
                }
                out.add(t);
                emitted.add(t.id());
                for (ResolvedTarget other : byId.values()) {
                    if (other.dependsOn().contains(t.id())) {
                        pending.merge(other.id(), -1, Integer::sum);
                    }
                }
                advanced = true;
            }
            if (!advanced) {
                throw new IllegalStateException("Ciclo entre targets: " + cycle());
            }
        }
        return out;
    }

    /** Targets cujas dependências estão todas em {@code done} e que ainda não iniciaram. */
    public List<ResolvedTarget> ready(Set<String> done, Set<String> startedOrDone) {
        List<ResolvedTarget> out = new ArrayList<>();
        for (ResolvedTarget t : byId.values()) {
            if (startedOrDone.contains(t.id())) {
                continue;
            }
            boolean depsDone = true;
            for (String dep : t.dependsOn()) {
                if (!done.contains(dep)) {
                    depsDone = false;
                    break;
                }
            }
            if (depsDone) {
                out.add(t);
            }
        }
        return out;
    }

    /** Subconjunto pedido + dependências transitivas, como novo grafo. */
    public TargetGraph subsetWithDependencies(Collection<String> ids) {
        Set<String> keep = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String id : ids) {
            if (id != null && byId.containsKey(id.trim())) {
                stack.push(id.trim());
            }
        }
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (!keep.add(id)) {
                continue;
            }
            ResolvedTarget t = byId.get(id);
            if (t != null) {
                t.dependsOn().forEach(stack::push);
            }
        }
        List<ResolvedTarget> subset = new ArrayList<>();
        for (ResolvedTarget t : byId.values()) {
            if (keep.contains(t.id())) {
                subset.add(t);
            }
        }
        return new TargetGraph(subset);
    }

    private Map<String, Integer> pendingDependencies() {
        Map<String, Integer> pending = new LinkedHashMap<>();
        for (ResolvedTarget t : byId.values()) {
            int count = 0;
            for (String dep : t.dependsOn()) {
                if (byId.containsKey(dep)) {
                    count++;
                }
            }
            pending.put(t.id(), count);
        }
        return pending;
    }
}
