package io.github.vecluu.jdoc.module;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Finds the reactor a module belongs to. Upwards it follows aggregators (a {@code pom.xml} in the parent directory
 * listing the current directory in {@code <modules>}) and local parents; from the topmost POM it walks down through
 * {@code <modules>} and collects the coordinates of every POM it reaches.
 */
@RequiredArgsConstructor
public final class ReactorDetector {

    private final PomHierarchy hierarchy;

    /** Detects the reactor of the module in {@code moduleDirectory}; a standalone or unreadable module is its own. */
    public Reactor detect(Path moduleDirectory) {
        Optional<PomModel> module = hierarchy.tryRead(moduleDirectory.resolve(ModuleLocator.POM_FILE));
        if (module.isEmpty()) {
            return new Reactor(moduleDirectory, Set.of());
        }
        Set<String> coordinates = new HashSet<>();
        Set<Path> visited = new HashSet<>();
        PomModel current = module.get();
        record(current, coordinates, visited);
        while (true) {
            PomModel from = current;
            Optional<PomModel> next = aggregatorAbove(from).or(() -> hierarchy.localParent(from))
                    .filter(candidate -> !visited.contains(candidate.file()));
            if (next.isEmpty()) {
                break;
            }
            current = next.get();
            record(current, coordinates, visited);
        }
        Path root = current.directory();
        collectModules(current, coordinates, visited);
        return new Reactor(root, coordinates);
    }

    private void collectModules(PomModel root, Set<String> coordinates, Set<Path> visited) {
        Deque<PomModel> queue = new ArrayDeque<>();
        Set<Path> expanded = new HashSet<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            PomModel pom = queue.poll();
            if (!expanded.add(pom.file())) {
                continue;
            }
            for (String module : pom.modules()) {
                Path file = modulePom(pom.directory(), module);
                if (expanded.contains(file) || !Files.isRegularFile(file)) {
                    continue;
                }
                hierarchy.tryRead(file).ifPresent(child -> {
                    record(child, coordinates, visited);
                    queue.add(child);
                });
            }
        }
    }

    private Optional<PomModel> aggregatorAbove(PomModel pom) {
        Path parentDirectory = pom.directory().getParent();
        if (parentDirectory == null) {
            return Optional.empty();
        }
        Path aggregator = parentDirectory.resolve(ModuleLocator.POM_FILE);
        if (!Files.isRegularFile(aggregator)) {
            return Optional.empty();
        }
        return hierarchy.tryRead(aggregator).filter(candidate -> candidate.modules().stream()
                .anyMatch(module -> modulePom(candidate.directory(), module).equals(pom.file())));
    }

    private static Path modulePom(Path directory, String module) {
        Path path = directory.resolve(module).normalize();
        return Files.isDirectory(path) ? path.resolve(ModuleLocator.POM_FILE) : path;
    }

    private static void record(PomModel pom, Set<String> coordinates, Set<Path> visited) {
        visited.add(pom.file());
        pom.coordinates().ifPresent(gav -> coordinates.add(gav.coordinates()));
    }
}
