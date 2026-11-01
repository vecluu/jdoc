package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.bytecode.RawClass.InnerClassEntry;
import io.github.vecluu.jdoc.bytecode.RawClass.RawField;
import io.github.vecluu.jdoc.bytecode.RawClass.RawMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** ASM visitor that records the facts {@link ClassFileReader} needs into a {@link RawClass}. Single use. */
final class ClassCollector extends ClassVisitor {

    static final String DEPRECATED_DESCRIPTOR = "Ljava/lang/Deprecated;";

    private int access;
    private String internalName;
    private String signature;
    private String superName;
    private List<String> interfaces = List.of();
    private final List<InnerClassEntry> innerClasses = new ArrayList<>();
    private boolean local;
    private boolean sealed;
    private boolean deprecated;
    private final List<RawField> fields = new ArrayList<>();
    private final List<RawMethod> methods = new ArrayList<>();

    ClassCollector() {
        super(Opcodes.ASM9);
    }

    /** The collected class; call after {@code ClassReader.accept}. */
    RawClass result() {
        return new RawClass(access, internalName, Optional.ofNullable(signature), Optional.ofNullable(superName),
                interfaces, innerClasses, local, sealed, deprecated, fields, methods);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        this.access = access;
        this.internalName = name;
        this.signature = signature;
        this.superName = superName;
        this.interfaces = interfaces == null ? List.of() : List.of(interfaces);
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        local = true;
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        sealed = true;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        innerClasses.add(new InnerClassEntry(name, Optional.ofNullable(outerName), Optional.ofNullable(innerName),
                access));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (DEPRECATED_DESCRIPTOR.equals(descriptor)) {
            deprecated = true;
        }
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM9) {
            private boolean fieldDeprecated;

            @Override
            public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                fieldDeprecated |= DEPRECATED_DESCRIPTOR.equals(annotation);
                return null;
            }

            @Override
            public void visitEnd() {
                fields.add(new RawField(access, name, descriptor, Optional.ofNullable(signature), fieldDeprecated));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                     String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {
            private boolean methodDeprecated;
            private final List<Optional<String>> parameterNames = new ArrayList<>();

            @Override
            public void visitParameter(String parameterName, int parameterAccess) {
                parameterNames.add(Optional.ofNullable(parameterName));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                methodDeprecated |= DEPRECATED_DESCRIPTOR.equals(annotation);
                return null;
            }

            @Override
            public void visitEnd() {
                methods.add(new RawMethod(access, name, descriptor, Optional.ofNullable(signature),
                        exceptions == null ? List.of() : List.of(exceptions), parameterNames, methodDeprecated));
            }
        };
    }
}
