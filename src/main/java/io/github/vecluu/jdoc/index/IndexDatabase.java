package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.IndexException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

/**
 * An open connection to {@code index.db}.
 *
 * <p>Opening creates the file if needed and switches it to WAL mode with foreign keys enforced and a busy timeout,
 * so a second process waits for a writer instead of failing. The schema is versioned with {@code PRAGMA user_version};
 * a file with another version is dropped and rebuilt (the index is a cache).
 *
 * <p>Write work is grouped with {@link #inTransaction(Supplier)}; nested calls join the outer transaction. An instance
 * wraps a single connection and must be confined to one thread at a time; open one instance per thread if needed.
 *
 * <p>{@link #openInBackground(CachePaths)} opens the file on a background thread and returns at once, so the caller
 * can do other work (e.g. resolve the module's classpath) while sqlite-jdbc loads its native library and the
 * connection is initialised. The first use of the instance waits for the open to finish and rethrows its failure.
 */
public final class IndexDatabase implements AutoCloseable {

    /** Default time a statement waits for another process's write lock. */
    public static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(60);

    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;

    /** Looked up on use only: initialising slf4j would otherwise delay starting the background open. */
    private static Logger log() {
        return LoggerFactory.getLogger(IndexDatabase.class);
    }

    private final Path file;
    private final Future<Connection> opening;
    private Connection connection;
    private int transactionDepth;

    private IndexDatabase(Path file, Connection connection) {
        this.file = file;
        this.opening = CompletableFuture.completedFuture(connection);
        this.connection = connection;
    }

    private IndexDatabase(Path file, Future<Connection> opening) {
        this.file = file;
        this.opening = opening;
    }

    /**
     * Starts opening the cache's {@code index.db} like {@link #open(CachePaths)}, on a background daemon thread, and
     * returns at once. The first call that needs the connection waits for the open; if it failed, that call throws
     * the open's {@link IndexException} (or {@link io.github.vecluu.jdoc.error.CacheIoException}).
     * {@link #close()} waits for a pending open and ignores its failure.
     */
    public static IndexDatabase openInBackground(CachePaths paths) {
        Objects.requireNonNull(paths, "paths");
        CompletableFuture<Connection> opening = new CompletableFuture<>();
        Thread.ofPlatform().name("jdoc-index-open").daemon().start(() -> {
            try {
                opening.complete(open(paths).connection);
            } catch (Throwable e) {
                opening.completeExceptionally(e);
            }
        });
        return new IndexDatabase(paths.indexDb(), opening);
    }

    /** Opens (creating if needed) the cache's {@code index.db}, creating the cache directory first. */
    public static IndexDatabase open(CachePaths paths) {
        paths.createRoot();
        return open(paths.indexDb(), DEFAULT_BUSY_TIMEOUT);
    }

    /**
     * Opens (creating if needed) an index database file and brings its schema up to date.
     *
     * @param file        the SQLite file; its parent directory must exist
     * @param busyTimeout how long to wait for another connection's lock before failing
     * @throws IndexException if the file cannot be opened or initialised
     */
    public static IndexDatabase open(Path file, Duration busyTimeout) {
        Objects.requireNonNull(file, "file");
        SQLiteConfig config = new SQLiteConfig();
        // WAL is switched on after connecting (enableWal): doing it in the config fails with SQLITE_BUSY when
        // several connections create a fresh file at once, because the busy handler does not cover that pragma.
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);
        config.setBusyTimeout((int) Math.min(Integer.MAX_VALUE, busyTimeout.toMillis()));
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection connection;
        try {
            connection = config.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
        } catch (SQLException e) {
            throw new IndexException("cannot open index database " + file + ": " + e.getMessage(), e);
        }
        IndexDatabase database = new IndexDatabase(file, connection);
        try {
            Instant deadline = Instant.now().plus(busyTimeout);
            database.retryWhileBusy(deadline, database::enableWal);
            database.retryWhileBusy(deadline, database::initialiseSchema);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
        return database;
    }

    /** The database file. */
    public Path file() {
        return file;
    }

    /**
     * Runs a unit of work in one transaction: commits when it returns, rolls back when it throws. Nested calls join
     * the outermost transaction. The transaction takes the write lock up front ({@code BEGIN IMMEDIATE}).
     *
     * @throws IndexException if the transaction cannot be started or committed
     */
    public <T> T inTransaction(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (transactionDepth > 0) {
            transactionDepth++;
            try {
                return work.get();
            } finally {
                transactionDepth--;
            }
        }
        begin();
        transactionDepth = 1;
        T result;
        try {
            result = work.get();
        } catch (RuntimeException | Error e) {
            transactionDepth = 0;
            rollback(e);
            throw e;
        }
        transactionDepth = 0;
        commit();
        return result;
    }

    /** {@link #inTransaction(Supplier)} for work without a result. */
    public void runInTransaction(Runnable work) {
        Objects.requireNonNull(work, "work");
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** True while a transaction started by {@link #inTransaction(Supplier)} is open. */
    public boolean inTransaction() {
        return transactionDepth > 0;
    }

    /** The schema version stored in the file. */
    public int schemaVersion() {
        try (Statement statement = connection().createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IndexException("cannot read schema version of " + file + ": " + e.getMessage(), e);
        }
    }

    /** The underlying connection, for this package's repositories; waits for a background open to finish. */
    Connection connection() {
        if (connection == null) {
            connection = awaitOpen();
        }
        return connection;
    }

    @Override
    public void close() {
        Connection open = connection;
        if (open == null) {
            try {
                open = awaitOpen();
            } catch (RuntimeException e) {
                log().debug("index database {} was never opened: {}", file, e.getMessage());
                return;
            }
        }
        try {
            open.close();
        } catch (SQLException e) {
            throw new IndexException("cannot close index database " + file + ": " + e.getMessage(), e);
        }
    }

    private Connection awaitOpen() {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return opening.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IndexException("cannot open index database " + file + ": " + cause.getMessage(), cause);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Runs a step of opening again while another connection holds a lock, with a growing pause, until the deadline.
     * Needed for the first open of a fresh file by several processes, where SQLite reports {@code SQLITE_BUSY}
     * immediately instead of waiting.
     */
    private void retryWhileBusy(Instant deadline, Runnable step) {
        long pauseMillis = 5;
        while (true) {
            try {
                step.run();
                return;
            } catch (IndexException e) {
                if (!isBusy(e) || Instant.now().plusMillis(pauseMillis).isAfter(deadline)) {
                    throw e;
                }
                log().debug("{} is busy while opening, retrying in {} ms", file, pauseMillis);
                sleep(pauseMillis);
                pauseMillis = Math.min(200, pauseMillis * 2);
            }
        }
    }

    private static boolean isBusy(IndexException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                int primary = sql.getErrorCode() & 0xff;
                if (primary == SQLITE_BUSY || primary == SQLITE_LOCKED) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexException("interrupted while waiting for the index database", e);
        }
    }

    private void enableWal() {
        try (Statement statement = connection().createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA journal_mode = WAL")) {
            String mode = rows.next() ? rows.getString(1) : "";
            if (!"wal".equalsIgnoreCase(mode)) {
                log().warn("{} does not support WAL mode (journal mode is {})", file, mode);
            }
        } catch (SQLException e) {
            throw new IndexException("cannot switch " + file + " to WAL mode: " + e.getMessage(), e);
        }
    }

    private void initialiseSchema() {
        runInTransaction(() -> {
            int version = schemaVersion();
            if (version == Schema.VERSION) {
                return;
            }
            if (version != 0) {
                log().debug("index schema version {} != {}, rebuilding {}", version, Schema.VERSION, file);
            }
            try (Statement statement = connection().createStatement()) {
                dropAll(statement);
                for (String ddl : Schema.STATEMENTS) {
                    statement.execute(ddl);
                }
                statement.execute("PRAGMA user_version = " + Schema.VERSION);
            } catch (SQLException e) {
                throw new IndexException("cannot create index schema in " + file + ": " + e.getMessage(), e);
            }
        });
    }

    private static void dropAll(Statement statement) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        statement.execute("PRAGMA defer_foreign_keys = ON");
        for (String table : tables) {
            statement.execute("DROP TABLE IF EXISTS \"" + table.replace("\"", "\"\"") + "\"");
        }
    }

    private void begin() {
        try {
            connection().setAutoCommit(false);
        } catch (SQLException e) {
            throw new IndexException("cannot begin transaction on " + file + ": " + e.getMessage(), e);
        }
    }

    private void commit() {
        try {
            // JDBC: switching auto-commit back on commits the open transaction.
            connection().setAutoCommit(true);
        } catch (SQLException e) {
            rollback(e);
            throw new IndexException("cannot commit transaction on " + file + ": " + e.getMessage(), e);
        }
    }

    private void rollback(Throwable cause) {
        try {
            connection().rollback();
            connection().setAutoCommit(true);
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }
}
