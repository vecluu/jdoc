package io.github.vecluu.jdoc.text;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The header line every type result starts with: name with type parameters, modifiers and kind keyword, artifact
 * label in parentheses, then tags, separated by two spaces.
 *
 * <pre>
 * java.util.Optional&lt;T&gt;  public final class  (jdk 25)
 * com.example.Model  public class  (sample-lib 1.0.0)  [from javadoc jar]
 * com.example.Util  public final class  (lib 2.0)  [decompiled, no docs]  [version unresolved]
 * </pre>
 *
 * @param type               the type the result is about
 * @param artifactLabel      the artifact label, e.g. {@code jdk 25} (see {@link ArtifactKey#label()})
 * @param versionUnresolved  true if the artifact version is a guess (adds {@code [version unresolved]})
 */
public record ResultHeader(TypeDescriptor type, String artifactLabel, boolean versionUnresolved) {

    /** Tag for results whose artifact version could not be resolved by Maven. */
    public static final String VERSION_UNRESOLVED_TAG = "[version unresolved]";

    private static final String SEPARATOR = "  ";

    public ResultHeader {
        Objects.requireNonNull(type, "type");
        Lines.requireSingleLine(artifactLabel);
    }

    /** A header for a type from the given artifact. */
    public static ResultHeader of(TypeDescriptor type, ArtifactKey artifact, boolean versionUnresolved) {
        return new ResultHeader(type, artifact.label(), versionUnresolved);
    }

    /** The header line. */
    public String line() {
        List<String> parts = new ArrayList<>();
        parts.add(type.name().displayName() + type.typeParameters());
        parts.add(declaration());
        parts.add("(" + artifactLabel + ")");
        type.docSource().headerTag().ifPresent(parts::add);
        if (versionUnresolved) {
            parts.add(VERSION_UNRESOLVED_TAG);
        }
        return String.join(SEPARATOR, parts);
    }

    private String declaration() {
        String modifiers = type.modifiers().strip().replaceAll("\\s+", " ");
        String keyword = type.kind().keyword();
        return modifiers.isEmpty() ? keyword : modifiers + " " + keyword;
    }

    /** Same as {@link #line()}. */
    @Override
    public String toString() {
        return line();
    }
}
