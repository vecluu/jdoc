package io.github.vecluu.jdoc.model;

/**
 * A row of the {@code members} table.
 *
 * @param id         database id
 * @param typeId     id of the declaring {@link IndexedType}
 * @param descriptor the member's data
 */
public record IndexedMember(long id, long typeId, MemberDescriptor descriptor) {
    public IndexedMember {
        Checks.notNull(descriptor, "descriptor");
    }
}
