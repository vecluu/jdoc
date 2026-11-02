package io.github.vecluu.jdoc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

class PrerequisitesTest {

    @Test
    void metPrerequisiteDoesNothingInAndOutsideCi() {
        assertThatCode(() -> Prerequisites.of(Map.of()).require(true, "tool")).doesNotThrowAnyException();
        assertThatCode(() -> Prerequisites.of(Map.of("CI", "true")).require(true, () -> {
            throw new IllegalStateException("message must not be built");
        })).doesNotThrowAnyException();
    }

    @Test
    void missingPrerequisiteSkipsOutsideCi() {
        Prerequisites local = Prerequisites.of(Map.of("CI", "false"));

        assertThat(local.ci()).isFalse();
        assertThatThrownBy(() -> local.require(false, "shellcheck not installed"))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("shellcheck not installed");
    }

    @Test
    void missingPrerequisiteFailsInCi() {
        Prerequisites ci = Prerequisites.of(Map.of("CI", " TRUE "));

        assertThat(ci.ci()).isTrue();
        assertThatThrownBy(() -> ci.require(false, () -> "no mvn"))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageContaining("CI=true")
                .hasMessageContaining("no mvn");
    }

    @Test
    void onlyTrueMarksCi() {
        assertThat(Prerequisites.of(Map.of()).ci()).isFalse();
        assertThat(Prerequisites.of(Map.of("CI", "")).ci()).isFalse();
        assertThat(Prerequisites.of(Map.of("CI", "1")).ci()).isFalse();
    }
}
