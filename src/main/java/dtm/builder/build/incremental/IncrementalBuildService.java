package dtm.builder.build.incremental;

import dtm.builder.build.Toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Decide o que precisa ser recompilado/relinkado comparando fonte, headers,
 * comando e toolchain com o estado do build anterior. Uma instancia por build;
 * os metodos operam sobre o TargetState confinado a thread de cada target.
 */
public final class IncrementalBuildService {

    /** Marca objetos cujo mapeamento de headers nao pode ser obtido. */
    static final String UNRESOLVED_DEPS = "<deps-indisponiveis>";

    private final Path buildDir;
    private final boolean enabled;
    private final String fingerprint;
    private final String buildMode;
    private final FileHasher hasher = new FileHasher();

    public IncrementalBuildService(Path buildDir, Toolchain toolchain, String buildMode,
                                   boolean enabled) {
        this.buildDir = buildDir;
        this.enabled = enabled;
        this.fingerprint = CompilerFingerprint.of(toolchain);
        this.buildMode = buildMode == null ? "" : buildMode;
    }

    public TargetState begin(String targetId) {
        TargetBuildState previous = enabled ? BuildStateStore.load(buildDir, targetId) : null;
        if (previous == null
                || !fingerprint.equals(previous.getToolchainFingerprint())
                || !buildMode.equals(previous.getBuildMode())) {
            previous = new TargetBuildState();
            previous.setToolchainFingerprint(fingerprint);
            previous.setBuildMode(buildMode);
        }
        return new TargetState(targetId, previous);
    }

    public boolean isObjectUpToDate(TargetState state, Path source, Path object,
                                    List<String> command) {
        String key = keyOf(source);
        state.touched().add(key);
        if (!enabled) {
            return false;
        }
        TargetBuildState.ObjectState os = state.data().getObjects().get(key);
        if (os == null || !Files.isRegularFile(object)) {
            return false;
        }
        if (!object.getFileName().toString().equals(os.getObjectFile())) {
            return false;
        }
        if (!FileHasher.hashCommand(command).equals(os.getCommandHash())) {
            return false;
        }
        if (!hasher.matches(source, os.getSourceStamp())) {
            return false;
        }
        if (os.getHeaders().containsKey(UNRESOLVED_DEPS)) {
            return false;
        }
        for (Map.Entry<String, String> header : os.getHeaders().entrySet()) {
            Path path = Path.of(header.getKey());
            if (!Files.isRegularFile(path) || !hasher.matches(path, header.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** headers == null indica que as dependencias nao puderam ser coletadas. */
    public void recordCompiled(TargetState state, Path source, Path object,
                               List<String> command, List<Path> headers) {
        state.touched().add(keyOf(source));
        state.markRecompiled();
        TargetBuildState.ObjectState os = new TargetBuildState.ObjectState();
        os.setObjectFile(object.getFileName().toString());
        os.setCommandHash(FileHasher.hashCommand(command));
        try {
            os.setSourceStamp(hasher.stamp(source));
            if (headers == null) {
                os.getHeaders().put(UNRESOLVED_DEPS, "");
            } else {
                Path normalizedSource = source.toAbsolutePath().normalize();
                for (Path header : headers) {
                    Path abs = header.toAbsolutePath().normalize();
                    if (abs.equals(normalizedSource)) {
                        continue;
                    }
                    try {
                        os.getHeaders().put(abs.toString(), hasher.stamp(abs));
                    } catch (IOException e) {
                        os.getHeaders().put(UNRESOLVED_DEPS, "");
                    }
                }
            }
        } catch (IOException e) {
            os.setSourceStamp("");
            os.getHeaders().put(UNRESOLVED_DEPS, "");
        }
        state.data().getObjects().put(keyOf(source), os);
    }

    public boolean needsLink(TargetState state, List<String> command, Path artifact,
                             List<Path> inputs) {
        if (!enabled || state.anyRecompiled()) {
            return true;
        }
        if (!Files.exists(artifact)) {
            return true;
        }
        if (!FileHasher.hashCommand(command).equals(state.data().getLinkCommandHash())) {
            return true;
        }
        Map<String, String> recorded = state.data().getLinkInputs();
        if (recorded.size() != inputs.size()) {
            return true;
        }
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            String stamp = recorded.get(normalized.toString());
            if (!Files.isRegularFile(normalized) || !hasher.matches(normalized, stamp)) {
                return true;
            }
        }
        return false;
    }

    public void recordLinked(TargetState state, List<String> command, List<Path> inputs) {
        state.data().setLinkCommandHash(FileHasher.hashCommand(command));
        state.data().getLinkInputs().clear();
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            try {
                state.data().getLinkInputs().put(normalized.toString(), hasher.stamp(normalized));
            } catch (IOException e) {
                state.data().setLinkCommandHash("");
                state.data().getLinkInputs().clear();
                return;
            }
        }
    }

    /** Link falhou: invalida o hash para que o proximo build sempre relinke. */
    public void recordLinkFailed(TargetState state) {
        state.data().setLinkCommandHash("");
        state.data().getLinkInputs().clear();
    }

    public void finish(TargetState state, Consumer<String> log) {
        state.data().getObjects().keySet().retainAll(state.touched());
        try {
            BuildStateStore.save(buildDir, state.targetId(), state.data());
        } catch (IOException e) {
            if (log != null) {
                log.accept("aviso: falha ao salvar estado incremental de "
                        + state.targetId() + ": " + e.getMessage());
            }
        }
    }

    private String keyOf(Path source) {
        return source.toAbsolutePath().normalize().toString();
    }
}
