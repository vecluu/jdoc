package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdkPackageFilterTest {

    private final JdkPackageFilter filter = new JdkPackageFilter();

    @Test
    void acceptsJavaJavaxAndJdkPackages() {
        assertThat(filter.includes("java.util", Optional.empty())).isTrue();
        assertThat(filter.includes("javax.net.ssl", Optional.empty())).isTrue();
        assertThat(filter.includes("jdk.jfr", Optional.empty())).isTrue();
    }

    @Test
    void rejectsOtherAndInternalPackages() {
        assertThat(filter.includes("sun.misc", Optional.empty())).isFalse();
        assertThat(filter.includes("com.sun.net.httpserver", Optional.empty())).isFalse();
        assertThat(filter.includes("javafoo.bar", Optional.empty())).isFalse();
        assertThat(filter.includes("jdk.internal.misc", Optional.empty())).isFalse();
        assertThat(filter.includes("jdk.xml.internal", Optional.empty())).isFalse();
        assertThat(filter.includes("java.internals", Optional.empty())).isTrue();
    }

    @Test
    void respectsKnownExports() {
        Optional<Set<String>> exports = Optional.of(Set.of("java.util"));
        assertThat(filter.includes("java.util", exports)).isTrue();
        assertThat(filter.includes("java.util.hidden", exports)).isFalse();
    }

    @Test
    void readsOnlyUnqualifiedExports() {
        String moduleInfo = """
                /** Module doc mentioning exports fake.pkg; in a comment. */
                module java.base {
                    exports java.lang;
                    exports java.util ;
                    // exports java.commented;
                    exports jdk.internal.misc to
                        java.desktop,
                        jdk.unsupported;
                    exports
                        java.io;
                    opens java.net;
                    uses java.util.spi.ToolProvider;
                }
                """;

        assertThat(JdkPackageFilter.unqualifiedExports(moduleInfo))
                .containsExactlyInAnyOrder("java.lang", "java.util", "java.io");
    }
}
