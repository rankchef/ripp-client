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
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DownloadingRipStore implements AutoCloseable {
    public static final int HASH_LENGTH = SeedingRipStore.HASH_LENGTH;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS downloads (
                id            TEXT PRIMARY KEY,
                file_path     TEXT    NOT NULL,
                file_name     TEXT    NOT NULL,
                file_size     INTEGER NOT NULL,
                chunk_size    INTEGER NOT NULL,
                chunk_hashes  BLOB    NOT NULL,
                completed     BLOB    NOT NULL
            )
            """;

    private final Connection connection;

    public DownloadingRipStore(Path dbFile) {
        try {
            Files.createDirectories(dbFile.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(CREATE_TABLE);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not open download database " + dbFile, e);
        }
    }

    /** Begin a download, or return the one already in progress without clearing finished chunks. */
    public synchronized Download start(UUID id, Path file, FileMetadata metadata) {
        Optional<Download> existing = find(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        int chunks = metadata.getTotalChunks();
        String sql = """
                INSERT INTO downloads
                    (id, file_path, file_name, file_size, chunk_size, chunk_hashes, completed)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setString(2, file.toAbsolutePath().toString());
            statement.setString(3, metadata.getFileName());
            statement.setLong(4, metadata.getFileSize());
            statement.setInt(5, metadata.getChunkSize());
            statement.setBytes(6, pack(metadata.getChunkHashes()));
            statement.setBytes(7, toBytes(new BitSet(chunks), chunks));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not start download " + id, e);
        }
        return find(id).orElseThrow();
    }

    public synchronized Optional<Download> find(UUID id) {
        String sql = """
                SELECT file_path, file_name, file_size, chunk_size, chunk_hashes, completed
                FROM downloads WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(id, row));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read download " + id, e);
        }
    }

    public synchronized List<Download> unfinished() {
        String sql = """
                SELECT id, file_path, file_name, file_size, chunk_size, chunk_hashes, completed
                FROM downloads ORDER BY rowid
                """;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<Download> downloads = new ArrayList<>();
            while (rows.next()) {
                Download download = read(UUID.fromString(rows.getString("id")), rows);
                if (!download.done()) {
                    downloads.add(download);
                }
            }
            return downloads;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list downloads", e);
        }
    }

    public synchronized void markCompleted(UUID id, int sequence) {
        Download download = find(id).orElseThrow(() -> new IllegalStateException("No download " + id));
        int total = download.metadata().getTotalChunks();
        if (sequence < 0 || sequence >= total) {
            throw new IllegalArgumentException("sequence out of range: " + sequence);
        }
        BitSet completed = download.completed();
        if (completed.get(sequence)) {
            return;
        }
        completed.set(sequence);
        String sql = "UPDATE downloads SET completed = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, toBytes(completed, total));
            statement.setString(2, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark chunk " + sequence + " of " + id, e);
        }
    }

    public synchronized void remove(UUID id) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM downloads WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove download " + id, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close download database", e);
        }
    }

    public record Download(UUID id, Path file, FileMetadata metadata, BitSet completed) {
        public List<Integer> missing() {
            int total = metadata.getTotalChunks();
            List<Integer> missing = new ArrayList<>();
            for (int sequence = 0; sequence < total; sequence++) {
                if (!completed.get(sequence)) {
                    missing.add(sequence);
                }
            }
            return missing;
        }

        public boolean done() {
            return metadata.getTotalChunks() == 0 || completed.cardinality() == metadata.getTotalChunks();
        }
    }

    private static Download read(UUID id, ResultSet row) throws SQLException {
        List<FileChunkHash> hashes = unpack(row.getBytes("chunk_hashes"));
        FileMetadata metadata = new FileMetadata(
                row.getString("file_name"),
                row.getLong("file_size"),
                row.getInt("chunk_size"),
                hashes
        );
        return new Download(
                id,
                Path.of(row.getString("file_path")),
                metadata,
                fromBytes(row.getBytes("completed"), hashes.size())
        );
    }

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

    private static List<FileChunkHash> unpack(byte[] packed) {
        if (packed.length % HASH_LENGTH != 0) {
            throw new IllegalStateException("chunk_hashes length is not a multiple of " + HASH_LENGTH);
        }
        int count = packed.length / HASH_LENGTH;
        List<FileChunkHash> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] hash = new byte[HASH_LENGTH];
            System.arraycopy(packed, i * HASH_LENGTH, hash, 0, HASH_LENGTH);
            hashes.add(new FileChunkHash(hash));
        }
        return hashes;
    }

    private static byte[] toBytes(BitSet bits, int totalChunks) {
        byte[] raw = bits.toByteArray();
        byte[] out = new byte[(totalChunks + 7) / 8];
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        return out;
    }

    private static BitSet fromBytes(byte[] bytes, int totalChunks) {
        BitSet bits = BitSet.valueOf(bytes == null ? new byte[0] : bytes);
        if (bits.length() > totalChunks) {
            bits.clear(totalChunks, bits.length());
        }
        return bits;
    }
}
