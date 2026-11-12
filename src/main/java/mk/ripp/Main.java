package mk.ripp;

import mk.ripp.file.FileMetadata;
import mk.ripp.file.FileSplitter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;

public class Main {
    public static void main(String[] args) throws IOException {
        FileSplitter splitter = new FileSplitter();
        FileMetadata metadata = splitter.createMetadata(Path.of("C:/Users/nikol/Desktop/SpremiTenderFinal.mp4"));
        System.out.println("File name " + metadata.getFileName());
        System.out.println("Size " + metadata.getFileSize());
        System.out.println("Chunk size " + metadata.getChunkSize());
        System.out.println("Hash " + Base64.getEncoder().encode(metadata.getChunkHashes().getFirst().getBytes()));
    }
}