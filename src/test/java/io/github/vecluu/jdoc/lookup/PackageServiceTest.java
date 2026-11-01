package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageServiceTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        SampleSources.addTo(index);
        services = index.services();
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void describesThePackageDocAndItsApiTypes() {
        PackageLookup lookup = services.packages().describe("a", index.scope());

        assertThat(lookup).isInstanceOfSatisfying(PackageLookup.Found.class, found -> {
            PackageView view = found.view();
            assertThat(view.doc()).hasValueSatisfying(doc ->
                    assertThat(doc.doc().description())
                            .isEqualTo("Named things and their implementations.\n\nMore about the package."));
            assertThat(view.types()).extracting(type -> type.name().relativeName())
                    .containsExactly("Base", "Impl", "Impl.Nested", "Named", "Other", "Point");
            assertThat(view.types().getFirst().descriptor().firstSentence())
                    .isEqualTo("Base class for named things.");
        });
    }

    @Test
    void packagesWithoutDocStillListTypes() {
        assertThat(services.packages().describe("java.lang", index.scope()))
                .isInstanceOfSatisfying(PackageLookup.Found.class, found -> {
                    assertThat(found.view().doc()).isEmpty();
                    assertThat(found.view().types()).extracting(type -> type.name().simpleName())
                            .containsExactly("Comparable", "Object");
                });
    }

    @Test
    void unknownPackagesSuggestSimilarNames() {
        assertThat(services.packages().describe("lang", index.scope()))
                .isInstanceOfSatisfying(PackageLookup.NotFound.class,
                        notFound -> assertThat(notFound.suggestions()).containsExactly("java.lang"));
        assertThat(services.packages().describe("nothing.here", index.scope()))
                .isInstanceOfSatisfying(PackageLookup.NotFound.class,
                        notFound -> assertThat(notFound.suggestions()).isEmpty());
    }
}
