package io.github.vecluu.jdoc.source;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import io.github.vecluu.jdoc.error.SourceParseException;
import io.github.vecluu.jdoc.javadoc.DocCommentRenderer;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses single {@code .java} files with the javac Tree API ({@code JavacTask.parse()} only: no attribution, no
 * classpath, no annotation processing) into {@link ParsedSourceFile}s.
 *
 * <p>One compiler and file manager are reused for every file, so create one parser per indexing run (or per
 * thread) and close it at the end. Syntax errors do not throw: the result is best-effort and lists them in
 * {@link ParsedSourceFile#problems()}. Not thread-safe.
 */
@Slf4j
public final class JavaSourceParser implements AutoCloseable {

    private static final List<String> OPTIONS = List.of("-proc:none", "-Xlint:none", "-Xdoclint:none");

    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;
    private final DocCommentRenderer renderer;

    /** A parser using the system Java compiler and the shared doc-comment rules. */
    public JavaSourceParser() {
        this(new DocCommentRenderer());
    }

    /**
     * A parser using the system Java compiler.
     *
     * @throws SourceParseException if the running Java has no compiler ({@code jdk.compiler} missing)
     */
    public JavaSourceParser(DocCommentRenderer renderer) {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new SourceParseException("no Java compiler available; jdoc must run on a JDK");
        }
        this.fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        this.renderer = renderer;
    }

    /** Parses a file without knowledge of other types (simple names resolve by documented guesses). */
    public ParsedSourceFile parse(JavaSource source) {
        return parse(source, KnownTypes.none());
    }

    /**
     * Parses a file.
     *
     * @param knownTypes types known to exist, to resolve same-package and on-demand-import names exactly
     * @throws SourceParseException if javac fails internally
     */
    public ParsedSourceFile parse(JavaSource source, KnownTypes knownTypes) {
        List<String> problems = new ArrayList<>();
        DiagnosticListener<JavaFileObject> listener = diagnostic -> {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                problems.add(diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() + ": "
                        + diagnostic.getMessage(Locale.ROOT));
            }
        };
        try {
            JavacTask task = (JavacTask) compiler.getTask(Writer.nullWriter(), fileManager, listener, OPTIONS, null,
                    List.of(new StringSource(source)));
            Iterator<? extends CompilationUnitTree> units = task.parse().iterator();
            if (!units.hasNext()) {
                throw new SourceParseException("javac produced no compilation unit for " + source.path());
            }
            ParsedSourceFile result = new CompilationUnitExtractor(source, units.next(), DocTrees.instance(task),
                    renderer, knownTypes).extract(problems);
            if (result.hasProblems()) {
                log.debug("syntax errors in {}: {}", source.path(), result.problems());
            }
            return result;
        } catch (IOException | RuntimeException e) {
            if (e instanceof SourceParseException parseException) {
                throw parseException;
            }
            throw new SourceParseException("failed to parse " + source.path() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            fileManager.close();
        } catch (IOException e) {
            throw new SourceParseException("failed to close the compiler file manager", e);
        }
    }

    /** In-memory source file; its URI keeps the entry path so {@code package-info}/{@code module-info} parse. */
    private static final class StringSource extends SimpleJavaFileObject {
        private final String text;

        StringSource(JavaSource source) {
            super(URI.create("string:///" + source.path().replace(' ', '_')), Kind.SOURCE);
            this.text = source.text();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
