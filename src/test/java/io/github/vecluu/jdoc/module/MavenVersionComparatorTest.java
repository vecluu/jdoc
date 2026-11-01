package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class MavenVersionComparatorTest {

    private final MavenVersionComparator comparator = new MavenVersionComparator();

    @ParameterizedTest
    @CsvSource({
            "1.0, 1.1",
            "1.9, 1.10",
            "1.0, 1.0.1",
            "1.0-SNAPSHOT, 1.0",
            "1.0-alpha-1, 1.0-beta",
            "1.0-beta-2, 1.0-milestone-1",
            "1.0-M1, 1.0-RC1",
            "1.0-RC1, 1.0-SNAPSHOT",
            "1.0-rc1, 1.0",
            "1.0, 1.0-sp1",
            "1.0-alpha1, 1.0-alpha2",
            "1.0-a1, 1.0-b1",
            "1.0-android, 1.0-jre",
            "1.0-jre, 1.0.1",
            "1.0-foo, 1-1",
            "2.0, 10.0",
            "1.0.0.20240101, 1.0.0.20240102",
            "99999999999999999999, 100000000000000000000",
            "0.9, 1",
    })
    void ordersOlderBeforeNewer(String older, String newer) {
        assertThat(comparator.compare(older, newer)).isNegative();
        assertThat(comparator.compare(newer, older)).isPositive();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1.0",
            "1.0, 1.0.0",
            "1.0, 1.0-final",
            "1.0, 1.0.GA",
            "1.0-RELEASE, 1.0",
            "1.0-cr1, 1.0-rc1",
            "1.0-SNAPSHOT, 1.0-snapshot",
    })
    void treatsEquivalentVersionsAsEqual(String left, String right) {
        assertThat(comparator.compare(left, right)).isZero();
    }

    @Test
    void sortsAListLikeMaven() {
        List<String> versions = new ArrayList<>(List.of("1.10", "1.2-SNAPSHOT", "1.2", "1.2-rc1", "1.1", "1.2.1"));
        Collections.shuffle(versions, new java.util.Random(7));

        versions.sort(comparator);

        assertThat(versions).containsExactly("1.1", "1.2-rc1", "1.2-SNAPSHOT", "1.2", "1.2.1", "1.10");
    }
}
