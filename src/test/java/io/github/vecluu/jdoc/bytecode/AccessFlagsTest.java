package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

class AccessFlagsTest {

    @Test
    void mapsVisibility() {
        assertThat(AccessFlags.visibility(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)).isEqualTo(Visibility.PUBLIC);
        assertThat(AccessFlags.visibility(Opcodes.ACC_PROTECTED)).isEqualTo(Visibility.PROTECTED);
        assertThat(AccessFlags.visibility(Opcodes.ACC_PRIVATE)).isEqualTo(Visibility.PRIVATE);
        assertThat(AccessFlags.visibility(Opcodes.ACC_FINAL)).isEqualTo(Visibility.PACKAGE);
    }

    @Test
    void mapsKinds() {
        int annotation = Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        assertThat(AccessFlags.kind(annotation, "java/lang/Object")).isEqualTo(TypeKind.ANNOTATION);
        assertThat(AccessFlags.kind(Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object"))
                .isEqualTo(TypeKind.INTERFACE);
        assertThat(AccessFlags.kind(Opcodes.ACC_ENUM | Opcodes.ACC_FINAL, "java/lang/Enum")).isEqualTo(TypeKind.ENUM);
        assertThat(AccessFlags.kind(Opcodes.ACC_RECORD | Opcodes.ACC_FINAL, "java/lang/Record"))
                .isEqualTo(TypeKind.RECORD);
        assertThat(AccessFlags.kind(Opcodes.ACC_FINAL, "java/lang/Record")).isEqualTo(TypeKind.RECORD);
        assertThat(AccessFlags.kind(Opcodes.ACC_PUBLIC, "java/lang/Object")).isEqualTo(TypeKind.CLASS);
        assertThat(AccessFlags.kind(Opcodes.ACC_PUBLIC, null)).isEqualTo(TypeKind.CLASS);
    }
}
