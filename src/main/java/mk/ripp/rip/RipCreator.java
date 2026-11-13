package mk.ripp.rip;

import mk.ripp.file.FileChunkHash;
import mk.ripp.file.FileMetadata;
import mk.ripp.file.FileSplitter;
import mk.ripp.store.RipStore;
import mk.ripp.tracker.TrackerClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class RipCreator {
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final FileSplitter files;
    private final TrackerClient tracker;
    private final RipStore rips;

    public RipCreator(FileSplitter files, TrackerClient tracker, RipStore rips) {
        this.files = files;
        this.tracker = tracker;
        this.rips = rips;
    }

    public UUID create(Path file) throws IOException, InterruptedException {
        FileMetadata metadata = files.createMetadata(file);
        check(metadata);
        UUID id = tracker.create(metadata);
        try {
            rips.insert(id, file, metadata);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Rip " + id + " was created on the tracker but not saved locally", e);
        }
        return id;
    }

    private static void check(FileMetadata metadata) {
        String fileName = metadata.getFileName();
        if (fileName == null || fileName.isBlank() || fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("fileName must be 1 to " + MAX_FILE_NAME_LENGTH + " characters");
        }
        if (metadata.getChunkSize() <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        long expected = metadata.getFileSize() == 0
                ? 0
                : Math.ceilDiv(metadata.getFileSize(), metadata.getChunkSize());
        List<FileChunkHash> hashes = metadata.getChunkHashes();
        if (expected > Integer.MAX_VALUE || hashes.size() != expected) {
            throw new IllegalArgumentException("chunkHashes must contain " + expected + " hashes");
        }
        for (int i = 0; i < hashes.size(); i++) {
            if (hashes.get(i).getBytes().length != RipStore.HASH_LENGTH) {
                throw new IllegalArgumentException(
                        "chunkHashes[" + i + "] must be " + RipStore.HASH_LENGTH + " bytes");
            }
        }
    }
}