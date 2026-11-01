package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MavenResultTest {

    @Test
    void summaryPrefersFirstErrorLine() {
        MavenResult result = new MavenResult(false, 1,
                "[WARNING] meh\n[ERROR] Failed to execute goal on project app\n[ERROR] second\n");

        assertThat(result.summary()).isEqualTo("Failed to execute goal on project app");
    }

    @Test
    void summaryFallsBackToLastLineThenExitCode() {
        assertThat(new MavenResult(false, 2, "first\nlast\n\n").summary()).isEqualTo("last");
        assertThat(new MavenResult(false, 2, null).summary()).isEqualTo("exit code 2");
        assertThat(MavenResult.failed("cannot run mvn").summary()).isEqualTo("cannot run mvn");
    }
}
