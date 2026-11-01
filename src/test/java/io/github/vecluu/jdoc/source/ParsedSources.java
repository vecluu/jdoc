package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeName;

/** Test helpers to parse snippets and pick types and members from the result. */
final class ParsedSources {

    private ParsedSources() {
    }

    static ParsedSourceFile parse(JavaSourceParser parser, String path, String text) {
        return parser.parse(new JavaSource(path, text, DocSource.SOURCES));
    }

    static ParsedType type(ParsedSourceFile file, String binaryName) {
        return file.type(TypeName.of(binaryName))
                .orElseThrow(() -> new AssertionError("no type " + binaryName + " in " + file.types()));
    }

    /** The only member with this erased key ({@code name(Erased,Types)} or {@code NAME}). */
    static ParsedMember member(ParsedType type, String erasedKey) {
        return type.members().stream()
                .filter(m -> m.descriptor().erasedKey().equals(erasedKey))
                .reduce((a, b) -> {
                    throw new AssertionError("several members " + erasedKey);
                })
                .orElseThrow(() -> new AssertionError("no member " + erasedKey + " in " + type.members().stream()
                        .map(m -> m.descriptor().erasedKey()).toList()));
    }

    static MemberDescriptor descriptor(ParsedType type, String erasedKey) {
        return member(type, erasedKey).descriptor();
    }

    static String signature(ParsedType type, String erasedKey) {
        return descriptor(type, erasedKey).signature();
    }
}
