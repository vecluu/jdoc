package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.Objects;
import java.util.Optional;

/** The implementation text of a member, for {@code jdoc source}. */
public sealed interface MemberSource permits MemberSource.Available, MemberSource.Unavailable {

    /** The member the source was asked for. */
    MemberMatch member();

    /**
     * The member's source.
     *
     * @param member     the member
     * @param code       the declaration with its body, re-indented; from source text it starts with the Javadoc
     *                   comment
     * @param doc        the rendered doc when it is not part of {@code code} (javadoc-jar types); else empty
     * @param decompiled true if {@code code} was produced by the decompiler
     */
    record Available(MemberMatch member, String code, Optional<RenderedDoc> doc, boolean decompiled)
            implements MemberSource {
        public Available {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(code, "code");
            doc = doc == null ? Optional.empty() : doc;
        }
    }

    /**
     * No source can be shown.
     *
     * @param member the member
     * @param reason one-line explanation, e.g. a compiler-generated member or a decompiler failure
     */
    record Unavailable(MemberMatch member, String reason) implements MemberSource {
        public Unavailable {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
