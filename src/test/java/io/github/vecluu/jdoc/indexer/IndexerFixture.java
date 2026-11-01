package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcDocRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Real components wired on one SQLite file, for the indexer ITs. Close it at the end of the test. */
final class IndexerFixture implements AutoCloseable {

    final IndexDatabase database;
    final JdbcTypeRepository types;
    final JdbcMemberRepository members;
    final JdbcArtifactRepository artifacts;
    final JdbcFailedArtifactRepository failures;
    final JdbcDocRepository docs;
    final SqliteIndexStore store;

    IndexerFixture(Path databaseFile) {
        database = IndexDatabase.open(databaseFile, Duration.ofSeconds(60));
        types = new JdbcTypeRepository(database);
        members = new JdbcMemberRepository(database);
        artifacts = new JdbcArtifactRepository(database);
        failures = new JdbcFailedArtifactRepository(database);
        docs = new JdbcDocRepository(database);
        store = new SqliteIndexStore(database, artifacts, types, members, failures, docs);
    }

    JarArtifactIndexer artifactIndexer() {
        return new JarArtifactIndexer(new ZipBinaryJarScanner(), new SiblingDocJarLocator(), new ZipArchiveOpener(),
                new JavadocPageParser(), JavaSourceParser::new, store, Clock.systemUTC());
    }

    IndexedType type(TypeName name, long artifactId) {
        List<IndexedType> found = types.findByBinaryName(name, List.of(artifactId));
        if (found.size() != 1) {
            throw new AssertionError("expected one " + name + " but found " + found);
        }
        return found.getFirst();
    }

    List<IndexedMember> members(TypeName name, long artifactId) {
        return members.membersOf(type(name, artifactId).id());
    }

    @Override
    public void close() {
        database.close();
    }
}
