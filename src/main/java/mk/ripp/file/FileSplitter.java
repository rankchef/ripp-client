package mk.ripp.file;

import mk.ripp.file.FileChunk;
import mk.ripp.file.FileChunkHash;
import mk.ripp.file.FileMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class FileSplitter {

    public static final int DEFAULT_CHUNK_SIZE = 512 * 1024; // 512 KB

    public FileMetadata createMetadata(Path filePath) throws IOException {
        return createMetadata(filePath, DEFAULT_CHUNK_SIZE);
    }

    public FileMetadata createMetadata(Path filePath, int chunkSize) throws IOException {
        List<FileChunkHash> hashes = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);

            while (channel.read(buffer) > 0) {
                buffer.flip();
                byte[] chunkData = new byte[buffer.remaining()];
                buffer.get(chunkData);
                hashes.add(digest(chunkData));
                buffer.clear();
            }

            String fileName = filePath.getFileName().toString();
            return new FileMetadata(fileName, fileSize, chunkSize, hashes);
        }
    }

    public FileChunk readChunk(Path filePath, int chunkIndex, int chunkSize) throws IOException {
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long position = (long) chunkIndex * chunkSize;
            long fileSize = channel.size();

            if (position >= fileSize) {
                throw new IndexOutOfBoundsException("Chunk index out of bounds: " + chunkIndex);
            }

            int length = (int) Math.min(chunkSize, fileSize - position);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.read(buffer, position);

            return new FileChunk(chunkIndex, buffer.array());
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