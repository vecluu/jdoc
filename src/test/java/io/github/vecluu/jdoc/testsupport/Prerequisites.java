package io.github.vecluu.jdoc.testsupport;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/**
 * Checks a test's environment prerequisites (an installed tool, a Maven, an offline-capable repository).
 *
 * <p>On a developer machine a missing prerequisite skips the test. In CI ({@code CI=true}, set by GitHub Actions)
 * it fails the test instead, so a broken CI setup can never turn a test into a silent skip.
 */
public final class Prerequisites {

    /** The environment variable that marks a CI run when it is {@code true}. */
    public static final String CI_VARIABLE = "CI";

    private final boolean ci;

    private Prerequisites(boolean ci) {
        this.ci = ci;
    }

    /** Prerequisites for the current process environment. */
    public static Prerequisites fromEnvironment() {
        return of(System.getenv());
    }

    /** Prerequisites for the given environment variables. */
    public static Prerequisites of(Map<String, String> environment) {
        return new Prerequisites("true".equalsIgnoreCase(environment.getOrDefault(CI_VARIABLE, "").strip()));
    }

    /** Whether a missing prerequisite fails instead of skipping. */
    public boolean ci() {
        return ci;
    }

    /** Skips the test (fails it in CI) unless {@code met}; {@code what} names the missing prerequisite. */
    public void require(boolean met, String what) {
        require(met, () -> what);
    }

    /**
     * Skips the test (fails it in CI) unless {@code met}.
     *
     * @param what describes the missing prerequisite; only evaluated when it is missing
     */
    public void require(boolean met, Supplier<String> what) {
        if (met) {
            return;
        }
        if (ci) {
            Assertions.fail("prerequisite missing in CI (" + CI_VARIABLE + "=true), not skipping: " + what.get());
        }
        Assumptions.abort("prerequisite missing: " + what.get());
    }
}
