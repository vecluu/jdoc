package io.github.vecluu.jdoc.source;

import java.util.Set;

/** Simple names of the public top-level types of {@code java.lang} (JDK 25), implicitly imported everywhere. */
final class JavaLangTypes {

    private static final Set<String> NAMES = Set.of(
            "AbstractMethodError", "Appendable", "ArithmeticException", "ArrayIndexOutOfBoundsException",
            "ArrayStoreException", "AssertionError", "AutoCloseable", "Boolean", "BootstrapMethodError", "Byte",
            "CharSequence", "Character", "Class", "ClassCastException", "ClassCircularityError", "ClassFormatError",
            "ClassLoader", "ClassNotFoundException", "ClassValue", "CloneNotSupportedException", "Cloneable",
            "Comparable", "Deprecated", "Double", "Enum", "EnumConstantNotPresentException", "Error", "Exception",
            "ExceptionInInitializerError", "Float", "FunctionalInterface", "IO", "IllegalAccessError",
            "IllegalAccessException", "IllegalArgumentException", "IllegalCallerException",
            "IllegalMonitorStateException", "IllegalStateException", "IllegalThreadStateException",
            "IncompatibleClassChangeError", "IndexOutOfBoundsException", "InheritableThreadLocal",
            "InstantiationError", "InstantiationException", "Integer", "InternalError", "InterruptedException",
            "Iterable", "LayerInstantiationException", "LinkageError", "Long", "MatchException", "Math", "Module",
            "ModuleLayer", "NegativeArraySizeException", "NoClassDefFoundError", "NoSuchFieldError",
            "NoSuchFieldException", "NoSuchMethodError", "NoSuchMethodException", "NullPointerException", "Number",
            "NumberFormatException", "Object", "OutOfMemoryError", "Override", "Package", "Process",
            "ProcessBuilder", "ProcessHandle", "Readable", "Record", "ReflectiveOperationException", "Runnable",
            "Runtime", "RuntimeException", "RuntimePermission", "SafeVarargs", "ScopedValue", "SecurityException",
            "SecurityManager", "Short", "StableValue", "StackOverflowError", "StackTraceElement", "StackWalker",
            "StrictMath", "String", "StringBuffer", "StringBuilder", "StringIndexOutOfBoundsException",
            "SuppressWarnings", "System", "Thread", "ThreadDeath", "ThreadGroup", "ThreadLocal", "Throwable",
            "TypeNotPresentException", "UnknownError", "UnsatisfiedLinkError", "UnsupportedClassVersionError",
            "UnsupportedOperationException", "VerifyError", "VirtualMachineError", "Void", "WrongThreadException");

    private JavaLangTypes() {
    }

    static boolean contains(String simpleName) {
        return NAMES.contains(simpleName);
    }
}
