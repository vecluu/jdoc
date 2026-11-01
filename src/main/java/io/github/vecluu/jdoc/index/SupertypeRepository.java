package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.Collection;
import java.util.List;

/**
 * Read access to the {@code supertypes} table (direct supertype edges). Rows are written together with their types by
 * {@link TypeRepository#insertAll(long, List)}, from each descriptor's supertype list.
 */
public interface SupertypeRepository {

    /** Direct supertypes of a type, superclass first, then interfaces in declaration order. */
    List<TypeName> supertypesOf(long typeId);

    /** Types in scope that name {@code supertype} as a direct supertype (for {@code impls}), ordered by name. */
    List<IndexedType> directSubtypesOf(TypeName supertype, Collection<Long> artifactIds);
}
