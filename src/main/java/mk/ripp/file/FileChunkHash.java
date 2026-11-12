package mk.ripp.file;

import java.util.Arrays;

public class FileChunkHash {
    private final byte[] bytes;

    public FileChunkHash(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes(){
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileChunkHash)) return false;
        FileChunkHash that = (FileChunkHash) o;
        return Arrays.equals(this.bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
