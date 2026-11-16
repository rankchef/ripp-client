package mk.ripp.file;

import mk.ripp.file.model.FileChunk;
import mk.ripp.file.model.FileChunkHash;
import mk.ripp.file.model.FileMetadata;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileSplitter {

    public static final int DEFAULT_CHUNK_SIZE = 512 * 1024; // 512 KB

    public FileMetadata createMetadata(Path filePath) throws IOException {
        return createMetadata(filePath, DEFAULT_CHUNK_SIZE);
    }

    public FileMetadata createMetadata(Path filePath, int chunkSize) throws IOException {
        List<FileChunkHash> hashes = new ArrayList<>();

        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileSize = file.length();
            byte[] buffer = new byte[chunkSize];
            int read;
            while ((read = file.read(buffer)) > 0) {
                byte[] chunkData = read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
                hashes.add(digest(chunkData));
            }

            String fileName = filePath.getFileName().toString();
            return new FileMetadata(fileName, fileSize, chunkSize, hashes);
        }
    }

    public FileChunk readChunk(Path filePath, int chunkIndex, int chunkSize) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            long position = (long) chunkIndex * chunkSize;
            long fileSize = file.length();

            if (position >= fileSize) {
                throw new IndexOutOfBoundsException("Chunk index out of bounds: " + chunkIndex);
            }

            int length = (int) Math.min(chunkSize, fileSize - position);
            byte[] data = new byte[length];
            file.seek(position);
            file.readFully(data);
            return new FileChunk(chunkIndex, data);
        }
    }

    private FileChunkHash digest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return new FileChunkHash(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
