package io.github.vecluu.jdoc.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdocExceptionTest {

    @Test
    void subclassesAreUncheckedJdocExceptionsKeepingCause() {
        IOException cause = new IOException("disk");
        List<JdocException> all = List.of(
                new ConfigurationException("c", cause), new ResolutionException("r", cause),
                new IndexException("i", cause), new JarReadException("j", cause),
                new SourceParseException("s", cause), new CacheIoException("f", cause));
        assertThat(all).allSatisfy(e -> {
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause()).isSameAs(cause);
        });
        assertThat(new JdocException("plain")).hasMessage("plain").hasNoCause();
    }
}
