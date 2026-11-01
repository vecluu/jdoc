package io.github.vecluu.jdoc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The binary name of a type, e.g. {@code java.util.Map$Entry}, plus everything derived from it.
 *
 * <p>Within the class part (after the last {@code .}), every {@code $} that separates two non-empty identifier
 * pieces is taken as a nesting separator. A {@code $} at the start or end of the class part, or next to another
 * {@code $}, stays part of the identifier ({@code $Proxy}, {@code Foo$}). Top-level class names that really contain
 * {@code $} in the middle are therefore misread as nested types; this is a documented limitation.
 *
 * @param binaryName the binary name, {@code .}-separated packages, {@code $}-separated nesting
 */
public record TypeName(String binaryName) implements Comparable<TypeName> {

    public TypeName {
        Checks.notBlank(binaryName, "binary name");
        if (binaryName.indexOf('/') >= 0 || binaryName.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("not a binary type name: '" + binaryName + "'");
        }
        for (String segment : binaryName.split("\\.", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("empty name segment in '" + binaryName + "'");
            }
        }
    }

    /** Factory from a binary name, {@code java.util.Map$Entry}. */
    public static TypeName of(String binaryName) {
        return new TypeName(binaryName);
    }

    /** Factory from a JVM internal name, {@code java/util/Map$Entry}. */
    public static TypeName fromInternalName(String internalName) {
        Checks.notBlank(internalName, "internal name");
        return new TypeName(internalName.replace('/', '.'));
    }

    /** Factory from a class entry path inside a jar, {@code java/util/Map$Entry.class}. */
    public static TypeName fromClassEntryPath(String entryPath) {
        Checks.notBlank(entryPath, "entry path");
        if (!entryPath.endsWith(".class")) {
            throw new IllegalArgumentException("not a class entry: " + entryPath);
        }
        return fromInternalName(entryPath.substring(0, entryPath.length() - ".class".length()));
    }

    /**
     * Factory from a package and the chain of simple names from the top-level type inwards.
     *
     * @param packageName  package, empty for the default package
     * @param simpleNames  {@code ["Map", "Entry"]}
     */
    public static TypeName of(String packageName, List<String> simpleNames) {
        Checks.notNull(packageName, "package name");
        if (simpleNames == null || simpleNames.isEmpty()) {
            throw new IllegalArgumentException("at least one simple name is required");
        }
        for (String simple : simpleNames) {
            Checks.notBlank(simple, "simple name");
            if (simple.indexOf('.') >= 0) {
                throw new IllegalArgumentException("simple name contains '.': " + simple);
            }
        }
        String classPart = String.join("$", simpleNames);
        return new TypeName(packageName.isEmpty() ? classPart : packageName + "." + classPart);
    }

    /**
     * Factory from a source-style display name whose last {@code nestingDepth + 1} segments are type names:
     * {@code fromDisplayName("java.util.Map.Entry", 1)} is {@code java.util.Map$Entry}.
     */
    public static TypeName fromDisplayName(String displayName, int nestingDepth) {
        Checks.notBlank(displayName, "display name");
        String[] segments = displayName.split("\\.", -1);
        if (nestingDepth < 0 || nestingDepth >= segments.length) {
            throw new IllegalArgumentException(
                    "nesting depth " + nestingDepth + " does not fit display name '" + displayName + "'");
        }
        int firstType = segments.length - nestingDepth - 1;
        String packageName = String.join(".", List.of(segments).subList(0, firstType));
        return of(packageName, List.of(segments).subList(firstType, segments.length));
    }

    /** The package, empty for the default package. */
    public String packageName() {
        int dot = binaryName.lastIndexOf('.');
        return dot < 0 ? "" : binaryName.substring(0, dot);
    }

    /** True if the type is in the default package. */
    public boolean isInDefaultPackage() {
        return binaryName.indexOf('.') < 0;
    }

    /** Simple names from the top-level type inwards, {@code [Map, Entry]}. */
    public List<String> simpleNames() {
        String classPart = classPart();
        List<String> names = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < classPart.length() - 1; i++) {
            if (classPart.charAt(i) == '$' && classPart.charAt(i - 1) != '$' && classPart.charAt(i + 1) != '$') {
                names.add(classPart.substring(start, i));
                start = i + 1;
            }
        }
        names.add(classPart.substring(start));
        return List.copyOf(names);
    }

    /** The innermost simple name, {@code Entry}. */
    public String simpleName() {
        List<String> names = simpleNames();
        return names.getLast();
    }

    /** Number of enclosing types: 0 for a top-level type. */
    public int nestingDepth() {
        return simpleNames().size() - 1;
    }

    /** True for member types. */
    public boolean isNested() {
        return nestingDepth() > 0;
    }

    /** The name relative to its package in source form, {@code Map.Entry}. */
    public String relativeName() {
        return String.join(".", simpleNames());
    }

    /** The canonical source-style name, {@code java.util.Map.Entry}. */
    public String displayName() {
        String packageName = packageName();
        return packageName.isEmpty() ? relativeName() : packageName + "." + relativeName();
    }

    /** The directly enclosing type, if this type is nested. */
    public Optional<TypeName> outerName() {
        List<String> names = simpleNames();
        if (names.size() == 1) {
            return Optional.empty();
        }
        return Optional.of(of(packageName(), names.subList(0, names.size() - 1)));
    }

    /** The outermost enclosing type, or this type if it is top-level. */
    public TypeName topLevel() {
        List<String> names = simpleNames();
        return names.size() == 1 ? this : of(packageName(), names.subList(0, 1));
    }

    /** The member type with the given simple name inside this type. */
    public TypeName nested(String simpleName) {
        Checks.notBlank(simpleName, "simple name");
        List<String> names = new ArrayList<>(simpleNames());
        names.add(simpleName);
        return of(packageName(), names);
    }

    /** The JVM internal name, {@code java/util/Map$Entry}. */
    public String internalName() {
        return binaryName.replace('.', '/');
    }

    /** The package as a path, {@code java/util}; empty for the default package. */
    public String packagePath() {
        return packageName().replace('.', '/');
    }

    /** Entry path of the top-level type's source file, {@code java/util/Map.java}. */
    public String sourceEntryPath() {
        return inPackagePath(topLevel().simpleName() + ".java");
    }

    /** Entry path of this type's class file, {@code java/util/Map$Entry.class}. */
    public String classEntryPath() {
        return internalName() + ".class";
    }

    /** Entry path of this type's javadoc page, {@code java/util/Map.Entry.html}. */
    public String javadocPagePath() {
        return inPackagePath(relativeName() + ".html");
    }

    @Override
    public int compareTo(TypeName other) {
        return binaryName.compareTo(other.binaryName);
    }

    /** The binary name. */
    @Override
    public String toString() {
        return binaryName;
    }

    private String classPart() {
        int dot = binaryName.lastIndexOf('.');
        return dot < 0 ? binaryName : binaryName.substring(dot + 1);
    }

    private String inPackagePath(String fileName) {
        String packagePath = packagePath();
        return packagePath.isEmpty() ? fileName : packagePath + "/" + fileName;
    }
}
