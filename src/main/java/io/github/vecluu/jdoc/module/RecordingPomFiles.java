package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link PomFiles} that, while {@link #record(Supplier)} runs, remembers every path it was asked about (read, tested
 * for existence or kind, or stat'ed), in first-use order. Outside a recording it just delegates. Confined to one
 * thread, like the resolver using it.
 */
public final class RecordingPomFiles implements PomFiles {

    private final PomFiles delegate;
    private Optional<Set<Path>> recording = Optional.empty();

    /** @param delegate the file access to record */
    public RecordingPomFiles(PomFiles delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Runs {@code work} and returns its result with the paths it touched through this instance (absolute,
     * normalized).
     *
     * @throws IllegalStateException if a recording is already running
     */
    public <T> Recorded<T> record(Supplier<T> work) {
        if (recording.isPresent()) {
            throw new IllegalStateException("already recording");
        }
        Set<Path> paths = new LinkedHashSet<>();
        recording = Optional.of(paths);
        try {
            T value = work.get();
            return new Recorded<>(value, paths);
        } finally {
            recording = Optional.empty();
        }
    }

    @Override
    public boolean isRegularFile(Path path) {
        touched(path);
        return delegate.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        touched(path);
        return delegate.isDirectory(path);
    }

    @Override
    public byte[] read(Path file) throws IOException {
        touched(file);
        return delegate.read(file);
    }

    @Override
    public FileTime lastModifiedTime(Path file) throws IOException {
        touched(file);
        return delegate.lastModifiedTime(file);
    }

    private void touched(Path path) {
        recording.ifPresent(paths -> paths.add(path.toAbsolutePath().normalize()));
    }

    /**
     * The result of a recorded piece of work.
     *
     * @param value the work's result
     * @param paths every path the work touched, in first-use order
     */
    public record Recorded<T>(T value, Set<Path> paths) {

        public Recorded {
            paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
        }
    }
}
