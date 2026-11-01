package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.javadoc.BlockTagFormatter;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Members the compiler generates that a reader of the API sees: the default constructor of a class without
 * constructors, {@code values()}/{@code valueOf(String)} of enums, and the canonical constructor,
 * {@code toString()}/{@code hashCode()}/{@code equals(Object o)} and accessors of records (each only if not
 * declared; in the order javac emits them into the class file). Docs mirror the text javadoc generates for them. Implicit members have no
 * source range.
 */
final class ImplicitMembers {

    private static final String VALUES_TEXT =
            "an array containing the constants of this enum class, in the order they are declared";

    private static final String RECORD_TO_STRING_TEXT = "Returns a string representation of this record class. "
            + "The representation contains the name of the class, followed by the name and value of each of the "
            + "record components.";
    private static final String RECORD_HASH_CODE_TEXT = "Returns a hash code value for this object. "
            + "The value is derived from the hash code of each of the record components.";
    private static final String RECORD_EQUALS_TEXT = "Indicates whether some other object is \"equal to\" this one. "
            + "The objects are equal if the other object is of the same class and if all the record components "
            + "are equal.";

    private final SignatureFormat signatures;
    private final BlockTagFormatter tags = new BlockTagFormatter();

    ImplicitMembers(SignatureFormat signatures) {
        this.signatures = signatures;
    }

    /** The no-argument constructor with the class's own access. */
    ParsedMember defaultConstructor(TypeName type, Visibility visibility) {
        String name = type.simpleName();
        return implicit(MemberDescriptor.builder()
                .declaringType(type)
                .name(name)
                .kind(MemberKind.CONSTRUCTOR)
                .signature(signatures.constructor(visibility, "", name, List.of(), List.of()))
                .visibility(visibility)
                .build(), new RenderedDoc("", "", List.of(), false));
    }

    /** {@code values()} and {@code valueOf(String)}, unless declared (which javac would reject anyway). */
    List<ParsedMember> enumMethods(TypeName type, List<ParsedMember> declared) {
        List<ParsedMember> result = new ArrayList<>();
        String simple = type.relativeName();
        if (!isDeclared(declared, "values()")) {
            String description = "Returns " + VALUES_TEXT + ".";
            result.add(implicit(method(type, "values", simple + "[]", List.of(), true),
                    new RenderedDoc(description, description, List.of(tags.returns(VALUES_TEXT)), false)));
        }
        if (!isDeclared(declared, "valueOf(String)")) {
            String description = "Returns the enum constant of this class with the specified name.";
            result.add(implicit(method(type, "valueOf", simple,
                            List.of(new ParameterInfo("name", "String", "String")), true),
                    new RenderedDoc(description, description, List.of(
                            tags.param("name", "the name of the enum constant to be returned."),
                            tags.returns("the enum constant with the specified name"),
                            tags.throwsTag("IllegalArgumentException",
                                    "if this enum class has no constant with the specified name"),
                            tags.throwsTag("NullPointerException", "if the argument is null")), false)));
        }
        return result;
    }

    /**
     * The canonical constructor (with the record's access), {@code toString()}, {@code hashCode()},
     * {@code equals(Object o)} and one public accessor per component, unless declared.
     *
     * @param components   the record components as parameters
     * @param componentDoc {@code @param} descriptions of the record comment, by component name
     */
    List<ParsedMember> recordMembers(TypeName type, Visibility visibility, List<ParameterInfo> components,
                                     Map<String, String> componentDoc, List<ParsedMember> declared) {
        List<ParsedMember> result = new ArrayList<>();
        String simple = type.simpleName();
        boolean canonicalDeclared = declared.stream()
                .map(ParsedMember::descriptor)
                .anyMatch(member -> member.kind() == MemberKind.CONSTRUCTOR
                        && member.erasedParameterTypes().equals(
                        components.stream().map(ParameterInfo::erasedType).toList()));
        if (!canonicalDeclared) {
            String description = "Creates an instance of a `" + simple + "` record class.";
            List<String> lines = components.stream()
                    .map(c -> tags.param(c.name(), componentDoc.getOrDefault(c.name(),
                            "the value for the `" + c.name() + "` record component")))
                    .toList();
            result.add(implicit(MemberDescriptor.builder()
                    .declaringType(type)
                    .name(simple)
                    .kind(MemberKind.CONSTRUCTOR)
                    .signature(signatures.constructor(visibility, "", simple, components, List.of()))
                    .parameters(components)
                    .visibility(visibility)
                    .build(), new RenderedDoc(description, description, lines, false)));
        }
        result.addAll(recordObjectMethods(type, declared));
        for (ParameterInfo component : components) {
            if (isDeclared(declared, component.name() + "()")) {
                continue;
            }
            String description = "Returns the value of the `" + component.name() + "` record component.";
            String returns = componentDoc.getOrDefault(component.name(),
                    "the value of the `" + component.name() + "` record component");
            String returnType = component.isVarargs()
                    ? component.type().substring(0, component.type().length() - 3) + "[]"
                    : component.type();
            result.add(implicit(method(type, component.name(), returnType, List.of(), false),
                    new RenderedDoc(description, description, List.of(tags.returns(returns)), false)));
        }
        return result;
    }

    /** {@code toString()}, {@code hashCode()} and {@code equals(Object o)}, with javadoc's generated texts. */
    private List<ParsedMember> recordObjectMethods(TypeName type, List<ParsedMember> declared) {
        List<ParsedMember> result = new ArrayList<>();
        if (!isDeclared(declared, "toString()")) {
            result.add(implicit(method(type, "toString", "String", List.of(), false), new RenderedDoc(
                    RECORD_TO_STRING_TEXT, "Returns a string representation of this record class.",
                    List.of(tags.returns("a string representation of this object")), false)));
        }
        if (!isDeclared(declared, "hashCode()")) {
            result.add(implicit(method(type, "hashCode", "int", List.of(), false), new RenderedDoc(
                    RECORD_HASH_CODE_TEXT, "Returns a hash code value for this object.",
                    List.of(tags.returns("a hash code value for this object")), false)));
        }
        if (!isDeclared(declared, "equals(Object)")) {
            result.add(implicit(method(type, "equals", "boolean",
                            List.of(new ParameterInfo("o", "Object", "Object")), false),
                    new RenderedDoc(RECORD_EQUALS_TEXT,
                            "Indicates whether some other object is \"equal to\" this one.",
                            List.of(tags.param("o", "the object with which to compare"),
                                    tags.returns("`true` if this object is the same as the `o` argument; "
                                            + "`false` otherwise.")), false)));
        }
        return result;
    }

    private MemberDescriptor method(TypeName type, String name, String returnType, List<ParameterInfo> parameters,
                                    boolean isStatic) {
        return MemberDescriptor.builder()
                .declaringType(type)
                .name(name)
                .kind(MemberKind.METHOD)
                .signature(signatures.method(Visibility.PUBLIC, isStatic, SignatureFormat.MethodStyle.PLAIN, "",
                        returnType, name, parameters, List.of()))
                .parameters(parameters)
                .visibility(Visibility.PUBLIC)
                .isStatic(isStatic)
                .build();
    }

    private static boolean isDeclared(List<ParsedMember> declared, String erasedKey) {
        return declared.stream().anyMatch(member -> member.descriptor().kind() == MemberKind.METHOD
                && member.descriptor().erasedKey().equals(erasedKey));
    }

    private static ParsedMember implicit(MemberDescriptor descriptor, RenderedDoc doc) {
        return new ParsedMember(descriptor.toBuilder().firstSentence(doc.firstSentence()).build(), doc, true);
    }
}
