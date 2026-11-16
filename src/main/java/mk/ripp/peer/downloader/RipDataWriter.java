package mk.ripp.peer.downloader;

import mk.ripp.file.model.FileChunkHash;
import mk.ripp.file.model.FileMetadata;
import mk.ripp.peer.model.RipDataResponse;
import mk.ripp.peer.model.RipDataStatus;
import mk.ripp.store.DownloadingRipStore;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RipDataWriter {
    private final DownloadingRipStore downloads;

    public RipDataWriter(DownloadingRipStore downloads) {
        this.downloads = downloads;
    }

    /** Write an OK chunk into the destination file and mark that sequence done. */
    public boolean write(RipDataResponse response) {
        if (response.status() != RipDataStatus.OK) {
            return false;
        }
        DownloadingRipStore.Download download = downloads.find(response.ripId())
                .orElseThrow(() -> new IllegalStateException("No download " + response.ripId()));
        FileMetadata metadata = download.metadata();
        int sequence = response.sequence();
        if (sequence >= metadata.getTotalChunks()) {
            return false;
        }
        if (download.completed().get(sequence)) {
            return true;
        }
        byte[] data = response.data();
        if (data.length != lengthOf(metadata, sequence)) {
            return false;
        }
        if (!metadata.getChunkHashes().get(sequence).equals(hash(data))) {
            return false;
        }
        writeAt(download, sequence, data);
        downloads.markCompleted(response.ripId(), sequence);
        return true;
    }

    private static int lengthOf(FileMetadata metadata, int sequence) {
        long start = (long) sequence * metadata.getChunkSize();
        long remaining = metadata.getFileSize() - start;
        return (int) Math.min(metadata.getChunkSize(), remaining);
    }

    private static void writeAt(DownloadingRipStore.Download download, int sequence, byte[] data) {
        try {
            if (download.file().getParent() != null) {
                Files.createDirectories(download.file().getParent());
            }
            try (RandomAccessFile file = new RandomAccessFile(download.file().toFile(), "rw")) {
                file.seek((long) sequence * download.metadata().getChunkSize());
                file.write(data);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write chunk " + sequence + " of " + download.id(), e);
        }
    }

    private static FileChunkHash hash(byte[] data) {
        try {
            return new FileChunkHash(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
