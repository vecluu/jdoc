package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void printsVersionWithoutExiting() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"--version"}, new PrintStream(out), new PrintStream(err));

        assertThat(code).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("jdoc ");
    }

    @Test
    void usageErrorExitsWithThree() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"class"}, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err));

        assertThat(code).isEqualTo(3);
        assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("error: Missing required parameter");
    }
}
