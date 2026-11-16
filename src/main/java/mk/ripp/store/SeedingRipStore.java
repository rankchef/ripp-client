package mk.ripp.store;

import mk.ripp.file.model.FileChunkHash;
import mk.ripp.file.model.FileMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SeedingRipStore implements AutoCloseable {
    public static final int HASH_LENGTH = 32;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS rips (
                id            TEXT PRIMARY KEY,
                file_path     TEXT    NOT NULL,
                file_name     TEXT    NOT NULL,
                file_size     INTEGER NOT NULL,
                chunk_size    INTEGER NOT NULL,
                chunk_hashes  BLOB    NOT NULL
            )
            """;

    private final Connection connection;

    public SeedingRipStore(Path dbFile) {
        try {
            Files.createDirectories(dbFile.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(CREATE_TABLE);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not open rip database " + dbFile, e);
        }
    }

    public synchronized void insert(UUID id, Path file, FileMetadata metadata) {
        String sql = """
                INSERT INTO rips (id, file_path, file_name, file_size, chunk_size, chunk_hashes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setString(2, file.toAbsolutePath().toString());
            statement.setString(3, metadata.getFileName());
            statement.setLong(4, metadata.getFileSize());
            statement.setInt(5, metadata.getChunkSize());
            statement.setBytes(6, pack(metadata.getChunkHashes()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert rip " + id, e);
        }
    }

    public synchronized Optional<SeededRip> find(UUID id) {
        String sql = "SELECT file_path, chunk_size FROM rips WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SeededRip(
                        id,
                        Path.of(row.getString("file_path")),
                        row.getInt("chunk_size")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read rip " + id, e);
        }
    }

    public synchronized List<UUID> ids() {
        String sql = "SELECT id FROM rips ORDER BY rowid";
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<UUID> ids = new ArrayList<>();
            while (rows.next()) {
                ids.add(UUID.fromString(rows.getString("id")));
            }
            return ids;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list rips", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close rip database", e);
        }
    }

    public record SeededRip(UUID id, Path file, int chunkSize) {}

    private static byte[] pack(List<FileChunkHash> hashes) {
        byte[] packed = new byte[hashes.size() * HASH_LENGTH];
        for (int i = 0; i < hashes.size(); i++) {
            byte[] hash = hashes.get(i).getBytes();
            if (hash.length != HASH_LENGTH) {
                throw new IllegalArgumentException("Chunk hash " + i + " must be " + HASH_LENGTH + " bytes");
            }
            System.arraycopy(hash, 0, packed, i * HASH_LENGTH, HASH_LENGTH);
        }
        return packed;
    }
}