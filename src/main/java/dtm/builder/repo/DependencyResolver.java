package dtm.builder.repo;

import dtm.builder.manifest.model.LibraryManifest;
import dtm.builder.manifest.model.ManifestPackagesModel;
import dtm.builder.manifest.model.ResolvedDependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolve constraints e dependencias transitivas com backtracking por versao. */
public final class DependencyResolver {

    private final GlobalRepository repository;
    private final LibraryInstaller installer;

    public DependencyResolver(GlobalRepository repository, LibraryInstaller installer) {
        this.repository = repository;
        this.installer = installer;
    }

    public List<ResolvedDependency> resolve(List<ManifestPackagesModel> declared)
            throws DependencyResolutionException {
        List<Requirement> pending = new ArrayList<>();
        try {
            for (ManifestPackagesModel pkg : declared) {
                pending.add(requirement(pkg, "manifest do projeto"));
            }
        } catch (IllegalArgumentException e) {
            throw new DependencyResolutionException(e.getMessage(), e);
        }
        Failure failure = new Failure();
        State solved;
        try {
            solved = solve(pending, new State(), failure);
        } catch (IllegalArgumentException e) {
            throw new DependencyResolutionException(e.getMessage(), e);
        }
        if (solved == null) {
            throw new DependencyResolutionException(failure.message == null
                    ? "Nao foi possivel resolver as dependencias" : failure.message);
        }
        return new ArrayList<>(solved.selected.values());
    }

    private State solve(List<Requirement> pending, State state, Failure failure) {
        if (pending.isEmpty()) {
            return state;
        }
        Requirement requirement = pending.get(0);
        List<Requirement> remaining = new ArrayList<>(pending.subList(1, pending.size()));
        State next = state.copy();
        next.constraints.computeIfAbsent(requirement.id(), ignored -> new ArrayList<>())
                .addAll(requirement.constraints());

        ResolvedDependency selected = next.selected.get(requirement.id());
        if (selected != null) {
            if (!matchesAll(selectedVersion(selected), next.constraints.get(requirement.id()))) {
                failure.message = conflict(requirement.id(), next.constraints.get(requirement.id()));
                return null;
            }
            if (requirement.expand() && next.expanded.add(requirement.id())) {
                prependDependencies(remaining, selected, requirement.id());
            }
            return solve(remaining, next, failure);
        }

        List<String> candidates = candidates(requirement, next.constraints.get(requirement.id()),
                failure);
        for (String version : candidates) {
            ResolvedDependency dependency = load(requirement.declared(), version, failure);
            if (dependency == null) {
                continue;
            }
            State branch = next.copy();
            branch.selected.put(requirement.id(), dependency);
            List<Requirement> branchPending = new ArrayList<>(remaining);
            if (requirement.expand()) {
                branch.expanded.add(requirement.id());
                prependDependencies(branchPending, dependency, requirement.id());
            }
            State solved = solve(branchPending, branch, failure);
            if (solved != null) {
                return solved;
            }
        }
        if (failure.message == null || !failure.message.contains(requirement.id())) {
            failure.message = conflict(requirement.id(), next.constraints.get(requirement.id()));
        }
        return null;
    }

    private List<String> candidates(Requirement requirement, List<ConstraintOrigin> constraints,
                                    Failure failure) {
        List<String> versions = new ArrayList<>(repository.availableVersions(requirement.id()));
        List<String> matching = versions.stream()
                .filter(version -> matchesAll(version, constraints)).toList();
        String exact = exactDownloadVersion(requirement.declared(), constraints);
        if (matching.isEmpty() && exact != null && requirement.declared().hasDownloadUrl()) {
            ManifestPackagesModel installable = copy(requirement.declared(), exact);
            try {
                installer.ensureInstalled(installable);
                versions = new ArrayList<>(repository.availableVersions(requirement.id()));
                matching = versions.stream()
                        .filter(version -> matchesAll(version, constraints)).toList();
            } catch (IOException e) {
                failure.message = "Falha ao instalar '" + requirement.id() + ":"
                        + exact + "': " + e.getMessage();
            }
        }
        return matching;
    }

    private ResolvedDependency load(ManifestPackagesModel requested, String version,
                                    Failure failure) {
        ManifestPackagesModel resolved = copy(requested, version);
        GlobalRepository.VariantResolution variant = repository.resolveVariant(resolved, null);
        if (!variant.found()) {
            return null;
        }
        Path manifestFile = variant.dir().resolve(GlobalRepository.GLOBAL_MANIFEST_FILE);
        try {
            LibraryManifest manifest = RepoJson.read(manifestFile, LibraryManifest.class);
            if (manifest.getVersion() != null && !manifest.getVersion().isBlank()
                    && PackageVersion.parse(manifest.getVersion())
                    .compareTo(PackageVersion.parse(version)) != 0) {
                failure.message = "Manifest de '" + requested.getId()
                        + "' declara versao " + manifest.getVersion()
                        + ", mas esta armazenado em " + version;
                return null;
            }
            return new ResolvedDependency(resolved, manifest, variant.dir(), manifestFile,
                    variant.token());
        } catch (IOException | IllegalArgumentException e) {
            failure.message = "Manifest invalido para '" + requested.getId() + ":"
                    + version + "': " + e.getMessage();
            return null;
        }
    }

    private void prependDependencies(List<Requirement> pending, ResolvedDependency parent,
                                     String origin) {
        List<ManifestPackagesModel> dependencies = parent.libraryManifest().getDependencies();
        for (int i = dependencies.size() - 1; i >= 0; i--) {
            pending.add(0, requirement(dependencies.get(i), origin + ":"
                    + selectedVersion(parent)));
        }
    }

    private Requirement requirement(ManifestPackagesModel declared, String origin) {
        if (declared == null || declared.getId() == null || declared.getId().isBlank()) {
            throw new IllegalArgumentException("Dependencia sem id em " + origin);
        }
        List<ConstraintOrigin> constraints = new ArrayList<>();
        try {
            if (declared.getVersion() != null && !declared.getVersion().isBlank()) {
                constraints.add(new ConstraintOrigin(VersionConstraint.exact(declared.getVersion()),
                        origin));
            }
            if (declared.getVersionConstraint() != null
                    && !declared.getVersionConstraint().isBlank()) {
                constraints.add(new ConstraintOrigin(
                        VersionConstraint.parse(declared.getVersionConstraint()), origin));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Constraint invalida para '" + declared.getId()
                    + "' em " + origin + ": " + e.getMessage(), e);
        }
        if (constraints.isEmpty()) {
            throw new IllegalArgumentException("Dependencia '" + declared.getId()
                    + "' sem version ou versionConstraint em " + origin);
        }
        return new Requirement(declared.getId().trim(), declared, List.copyOf(constraints),
                declared.isTransitive());
    }

    private static boolean matchesAll(String version, List<ConstraintOrigin> constraints) {
        PackageVersion parsed;
        try {
            parsed = PackageVersion.parse(version);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return constraints.stream().allMatch(item -> item.constraint().matches(parsed));
    }

    private static String exactDownloadVersion(ManifestPackagesModel declared,
                                               List<ConstraintOrigin> constraints) {
        if (declared.getVersion() != null && !declared.getVersion().isBlank()) {
            return matchesAll(declared.getVersion(), constraints)
                    ? declared.getVersion().trim() : null;
        }
        String expression = declared.getVersionConstraint();
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            PackageVersion.parse(expression);
            return matchesAll(expression, constraints) ? expression.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String selectedVersion(ResolvedDependency dependency) {
        return dependency.declared().getVersion();
    }

    private static String conflict(String id, List<ConstraintOrigin> constraints) {
        Set<String> descriptions = new LinkedHashSet<>();
        for (ConstraintOrigin constraint : constraints) {
            descriptions.add(constraint.constraint() + " (" + constraint.origin() + ")");
        }
        return "Conflito de versao para '" + id + "': " + String.join(", ", descriptions);
    }

    private static ManifestPackagesModel copy(ManifestPackagesModel source, String version) {
        ManifestPackagesModel copy = new ManifestPackagesModel();
        copy.setId(source.getId());
        copy.setVersion(version);
        copy.setVersionConstraint(source.getVersionConstraint());
        copy.setDownloadUrl(source.getDownloadUrl());
        copy.setTransitive(source.isTransitive());
        return copy;
    }

    private record Requirement(String id, ManifestPackagesModel declared,
                               List<ConstraintOrigin> constraints, boolean expand) {
    }

    private record ConstraintOrigin(VersionConstraint constraint, String origin) {
    }

    private static final class State {
        private final LinkedHashMap<String, ResolvedDependency> selected = new LinkedHashMap<>();
        private final LinkedHashMap<String, List<ConstraintOrigin>> constraints =
                new LinkedHashMap<>();
        private final Set<String> expanded = new LinkedHashSet<>();

        private State copy() {
            State copy = new State();
            copy.selected.putAll(selected);
            constraints.forEach((id, values) -> copy.constraints.put(id,
                    new ArrayList<>(values)));
            copy.expanded.addAll(expanded);
            return copy;
        }
    }

    private static final class Failure {
        private String message;
    }
}
