package mk.ripp.file;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileChunk {
    private final int index;
    private final byte[] data;

    public FileChunk(int index, byte[] data){
        this.index = index;
        this.data = data;
    }

    public int getIndex(){
        return index;
    }

    public byte[] getData(){
        return data;
    }

    public FileChunkHash getHash(){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return new FileChunkHash(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
