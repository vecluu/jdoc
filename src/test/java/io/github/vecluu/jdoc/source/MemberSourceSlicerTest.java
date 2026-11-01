package io.github.vecluu.jdoc.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MemberSourceSlicerTest {

    private static final String TEXT = """
            package p;

            public class T {

                /**
                 * Adds one.
                 */
                public int inc(int x) {
                    return x + 1;
                }

                public void plain() {
                    // body
                }
            }
            """;

    private final JavaSourceParser parser = new JavaSourceParser();
    private final MemberSourceSlicer slicer = new MemberSourceSlicer();

    @AfterEach
    void close() {
        parser.close();
    }

    private MemberDescriptor member(String key) {
        return ParsedSources.descriptor(ParsedSources.type(ParsedSources.parse(parser, "p/T.java", TEXT), "p.T"), key);
    }

    @Test
    void wholeMemberIsDedented() {
        assertThat(slicer.member(TEXT, member("inc(int)"))).contains("""
                /**
                 * Adds one.
                 */
                public int inc(int x) {
                    return x + 1;
                }""");
    }

    @Test
    void docCommentAlone() {
        assertThat(slicer.docComment(TEXT, member("inc(int)"))).contains("/**\n * Adds one.\n */");
        assertThat(slicer.docComment(TEXT, member("plain()"))).isEmpty();
    }

    @Test
    void declarationWithoutDoc() {
        assertThat(slicer.declaration(TEXT, member("inc(int)")))
                .contains("public int inc(int x) {\n    return x + 1;\n}");
        assertThat(slicer.declaration(TEXT, member("plain()")))
                .contains("public void plain() {\n    // body\n}");
    }

    @Test
    void membersWithoutRangeHaveNoText() {
        MemberDescriptor implicit = ParsedSources.descriptor(
                ParsedSources.type(ParsedSources.parse(parser, "p/T.java", TEXT), "p.T"), "T()");
        assertThat(slicer.member(TEXT, implicit)).isEmpty();
        assertThat(slicer.declaration(TEXT, implicit)).isEmpty();
    }

    @Test
    void rangeBeyondTextIsEmpty() {
        assertThat(slicer.slice("abc", new SourceRange(1, 10))).isEmpty();
        assertThat(slicer.slice("  a\n  b", new SourceRange(2, 7))).contains("a\nb");
    }

    @Test
    void worksWithStoredDescriptors() {
        MemberDescriptor stored = MemberDescriptor.builder()
                .declaringType(TypeName.of("p.T"))
                .name("x")
                .kind(MemberKind.FIELD)
                .signature("int x")
                .visibility(Visibility.PUBLIC)
                .range(Optional.of(new SourceRange(0, 10)))
                .build();
        assertThat(slicer.member(TEXT, stored)).contains("package p;");
    }
}
