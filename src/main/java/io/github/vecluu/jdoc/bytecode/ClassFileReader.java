package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.RawClass.InnerClassEntry;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

/**
 * Reads one class file with ASM ({@code SKIP_CODE}) into a {@link ScannedClass}: kind, visibility, modifiers,
 * type parameters and supertypes of the type, plus its public and protected members with rendered signatures in
 * the architecture's signature format (simple names, parameter names from {@code MethodParameters} else
 * {@code argN}).
 *
 * <p>Skipped entirely (empty result): {@code module-info} and {@code package-info}, synthetic classes, anonymous and
 * local classes and anything nested inside them. Package-private and private types are returned with their
 * visibility; callers decide whether to keep them. Stateless and thread-safe.
 */
public final class ClassFileReader {

    private final SignatureParser parser = new SignatureParser();

    /**
     * Reads a class file.
     *
     * @param classBytes the class file content
     * @return the type and its API members, or empty if the class is not a named, non-synthetic type
     * @throws IllegalArgumentException if the bytes are not a valid class file
     */
    public Optional<ScannedClass> read(byte[] classBytes) {
        RawClass raw = collect(classBytes);
        if (!isNamedType(raw)) {
            return Optional.empty();
        }
        TypeDescriptor type = new TypeDescriptorFactory(parser).create(raw);
        List<MemberDescriptor> members = new MemberDescriptorFactory(parser, raw, type).create();
        return Optional.of(new ScannedClass(type, members));
    }

    private static RawClass collect(byte[] classBytes) {
        ClassCollector collector = new ClassCollector();
        try {
            new ClassReader(classBytes).accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid class file: " + e.getMessage(), e);
        }
        return collector.result();
    }

    /** False for module/package-info, synthetic, anonymous and local classes and classes nested in those. */
    static boolean isNamedType(RawClass raw) {
        String name = raw.internalName();
        if ((raw.access() & Opcodes.ACC_MODULE) != 0 || name.equals("module-info")
                || name.endsWith("/package-info") || name.equals("package-info")) {
            return false;
        }
        if ((raw.access() & Opcodes.ACC_SYNTHETIC) != 0 || raw.local()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        Optional<InnerClassEntry> entry = raw.ownEntry();
        while (entry.isPresent() && seen.add(entry.get().name())) {
            InnerClassEntry e = entry.get();
            if (e.outerName().isEmpty() || e.innerName().isEmpty() || (e.access() & Opcodes.ACC_SYNTHETIC) != 0) {
                return false;
            }
            entry = raw.entry(e.outerName().get());
        }
        return true;
    }
}
