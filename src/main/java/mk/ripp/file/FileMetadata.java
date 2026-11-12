package mk.ripp.file;

import java.util.List;

public class FileMetadata {

    private final String fileName;
    private final long fileSize;
    private final int chunkSize;
    private final List<FileChunkHash> chunkHashes;

    public FileMetadata(String fileName, long fileSize, int chunkSize, List<FileChunkHash> chunkHashes){
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.chunkHashes = chunkHashes;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public List<FileChunkHash> getChunkHashes() {
        return chunkHashes;
    }

    public int getTotalChunks() {
        return chunkHashes.size();
    }
}
