package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.Gav;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes a module's POM key: SHA-256 over the module directory, the module POM and every local ancestor POM
 * reached through {@code <relativePath>}. A parent from the repository adds nothing, unless it is a SNAPSHOT, in
 * which case its repository POM's mtime is added.
 */
@Slf4j
@RequiredArgsConstructor
public final class PomKeyCalculator {

    private final PomHierarchy hierarchy;
    private final Path localRepository;

    /**
     * Computes the key of the module whose POM is {@code modulePom}. A module POM that cannot be parsed is hashed on
     * its own (Maven will report the actual error).
     *
     * @throws ResolutionException if the module POM cannot be read at all
     */
    public PomKey calculate(Path modulePom) {
        Path pom = modulePom.toAbsolutePath().normalize();
        List<Path> localPoms = new ArrayList<>();
        Optional<ParentReference> repositoryParent = Optional.empty();
        try {
            LocalPomChain chain = hierarchy.localChain(pom);
            chain.poms().forEach(model -> localPoms.add(model.file()));
            repositoryParent = chain.repositoryParent();
        } catch (ResolutionException e) {
            log.debug("hashing unparsable module POM on its own: {}", e.getMessage());
            localPoms.clear();
            localPoms.add(pom);
        }

        MessageDigest key = sha256();
        update(key, "module " + pom.getParent() + "\n");
        List<PomFingerprint> fingerprints = new ArrayList<>();
        for (Path file : localPoms) {
            String hash = HexFormat.of().formatHex(sha256().digest(readBytes(file)));
            fingerprints.add(new PomFingerprint(file, hash));
            update(key, "pom " + file + " " + hash + "\n");
        }
        repositoryParent.map(ParentReference::gav).filter(Gav::isSnapshot).ifPresent(gav -> {
            Path file = localRepository.resolve(gav.pomPath());
            String mtime = "mtime:" + modifiedMillis(file);
            fingerprints.add(new PomFingerprint(file, mtime));
            update(key, "snapshot-parent " + gav + " " + mtime + "\n");
        });
        return new PomKey(HexFormat.of().formatHex(key.digest()), fingerprints);
    }

    private static String modifiedMillis(Path file) {
        try {
            return Long.toString(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            return "absent";
        }
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ResolutionException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
