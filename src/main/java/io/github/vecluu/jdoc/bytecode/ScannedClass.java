package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.util.List;

/**
 * One type read from a class file: its descriptor and its public/protected members.
 *
 * <p>The type descriptor has no doc: {@code docSource} is {@link io.github.vecluu.jdoc.model.DocSource#DECOMPILED},
 * {@code entryPath} the class entry ({@code a/b/Outer$Inner.class}) and the first sentence empty. The indexer
 * replaces these (via {@code toBuilder()}) once it has chosen the type's doc source.
 *
 * @param type    the type
 * @param members public and protected members in class-file order (fields first, then methods)
 */
public record ScannedClass(TypeDescriptor type, List<MemberDescriptor> members) {
    public ScannedClass {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        members = members == null ? List.of() : List.copyOf(members);
    }
}
