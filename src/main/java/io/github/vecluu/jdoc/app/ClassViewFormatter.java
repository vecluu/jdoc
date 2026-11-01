package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.lookup.ClassView;
import io.github.vecluu.jdoc.lookup.InheritedMembers;
import io.github.vecluu.jdoc.lookup.LocatedType;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.Supertype;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.text.ColumnLayout;
import io.github.vecluu.jdoc.text.TextResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formats {@code jdoc class}:
 *
 * <pre>
 * java.util.ArrayList&lt;E&gt;  public class  (jdk 25)
 * extends java.util.AbstractList  implements java.util.List, java.util.RandomAccess
 * Resizable-array implementation of the {@code List} interface.
 *
 * ArrayList()                     Constructs an empty list with an initial capacity of ten.
 * boolean add(E e)                Appends the specified element to the end of this list.
 * inherited from AbstractList: equals, hashCode
 * inherited from Object: getClass, notify, notifyAll, wait
 * </pre>
 *
 * The supertypes line leaves out what the kind implies ({@code Object}, {@code Enum}, {@code Record},
 * {@code Annotation}) and is omitted when nothing is left. Member rows are signature plus first sentence (a
 * deprecated member's sentence starts with {@code [deprecated]}); nested types follow as
 * {@code interface Map.Entry<K, V>} rows.
 */
final class ClassViewFormatter {

    private static final Set<TypeName> IMPLIED = Set.of(
            TypeName.of("java.lang.Object"), TypeName.of("java.lang.Enum"), TypeName.of("java.lang.Record"),
            TypeName.of("java.lang.annotation.Annotation"));
    private static final String DEPRECATED = "[deprecated]";

    private final ColumnLayout columns;

    ClassViewFormatter(ColumnLayout columns) {
        this.columns = columns;
    }

    TextResult format(ClassView view, LookupScope scope) {
        TextResult.Builder text = TextResult.builder(Headers.type(view.type(), scope));
        boolean preamble = false;
        String supertypes = supertypesLine(view.type().descriptor(), view.directSupertypes());
        if (!supertypes.isEmpty()) {
            text.line(supertypes);
            preamble = true;
        }
        List<String> paragraph = DocLines.split(view.firstParagraph());
        if (!paragraph.isEmpty()) {
            text.block(paragraph);
            preamble = true;
        }
        List<ColumnLayout.Row> rows = new ArrayList<>();
        view.members().stream().map(IndexedMember::descriptor).map(ClassViewFormatter::memberRow).forEach(rows::add);
        view.nestedTypes().stream().map(ClassViewFormatter::nestedRow).forEach(rows::add);
        if (preamble && (!rows.isEmpty() || !view.inherited().isEmpty())) {
            text.blankLine();
        }
        text.lines(columns.format(rows));
        view.inherited().stream().map(ClassViewFormatter::inheritedLine).forEach(text::line);
        return text.build();
    }

    static String supertypesLine(TypeDescriptor type, List<Supertype> supertypes) {
        List<String> names = supertypes.stream()
                .map(Supertype::name)
                .filter(name -> !IMPLIED.contains(name))
                .map(TypeName::displayName)
                .toList();
        if (names.isEmpty()) {
            return "";
        }
        boolean hasSuperclass = type.kind() == TypeKind.CLASS && !supertypes.isEmpty()
                && !IMPLIED.contains(supertypes.getFirst().name());
        if (type.kind() == TypeKind.INTERFACE || type.kind() == TypeKind.ANNOTATION) {
            return "extends " + String.join(", ", names);
        }
        if (hasSuperclass) {
            String superclass = names.getFirst();
            List<String> interfaces = names.subList(1, names.size());
            return interfaces.isEmpty()
                    ? "extends " + superclass
                    : "extends " + superclass + "  implements " + String.join(", ", interfaces);
        }
        return "implements " + String.join(", ", names);
    }

    private static ColumnLayout.Row memberRow(MemberDescriptor member) {
        return ColumnLayout.Row.of(member.signature(), sentence(member.firstSentence(), member.deprecated()));
    }

    private static ColumnLayout.Row nestedRow(LocatedType nested) {
        TypeDescriptor type = nested.descriptor();
        String left = type.kind().keyword() + " " + type.name().relativeName() + type.typeParameters();
        return ColumnLayout.Row.of(left, sentence(type.firstSentence(), type.deprecated()));
    }

    private static String sentence(String firstSentence, boolean deprecated) {
        String text = firstSentence == null ? "" : firstSentence.strip();
        if (!deprecated) {
            return text;
        }
        return text.isEmpty() ? DEPRECATED : DEPRECATED + " " + text;
    }

    private static String inheritedLine(InheritedMembers inherited) {
        return "inherited from " + inherited.declaringType().name().relativeName() + ": "
                + inherited.names().stream().collect(Collectors.joining(", "));
    }
}
