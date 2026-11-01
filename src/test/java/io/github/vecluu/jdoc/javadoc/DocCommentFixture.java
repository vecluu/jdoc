package io.github.vecluu.jdoc.javadoc;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Parses a doc comment with javac so renderer tests can work from comment text. */
final class DocCommentFixture {

    private DocCommentFixture() {
    }

    /**
     * The {@link DocCommentTree} of a class whose comment has the given lines (each prefixed with {@code " * "}).
     */
    static DocCommentTree comment(String... lines) {
        StringBuilder source = new StringBuilder("/**\n");
        for (String line : lines) {
            source.append(" * ").append(line).append('\n');
        }
        source.append(" */\nclass X {}\n");
        return parse(source.toString());
    }

    /** The doc comment of the first class in {@code source}, or {@code null} if it has none. */
    static DocCommentTree parse(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleJavaFileObject file = new SimpleJavaFileObject(URI.create("string:///X.java"),
                SimpleJavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(Writer.nullWriter(), null, d -> { }, List.of("-proc:none"),
                null, List.of(file));
        try {
            CompilationUnitTree unit = task.parse().iterator().next();
            ClassTree type = (ClassTree) unit.getTypeDecls().getFirst();
            return DocTrees.instance(task).getDocCommentTree(new TreePath(new TreePath(unit), type));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
