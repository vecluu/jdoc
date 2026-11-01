package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.query.NameMatch;
import java.util.Objects;
import java.util.Optional;

/**
 * One {@code jdoc find} result line.
 *
 * @param reference {@code java.util.Optional} for types, {@code java.util.Optional#of(Object)} for members
 * @param type      the type, or the member's declaring type
 * @param member    the member, for member hits
 * @param score     how the name matched
 */
public record FindHit(String reference, LocatedType type, Optional<IndexedMember> member, NameMatch score) {

    public FindHit {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(type, "type");
        member = member == null ? Optional.empty() : member;
        Objects.requireNonNull(score, "score");
    }

    /** True for a member hit, false for a type hit. */
    public boolean isMember() {
        return member.isPresent();
    }
}
