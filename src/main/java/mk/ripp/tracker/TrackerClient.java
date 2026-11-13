package mk.ripp.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mk.ripp.file.FileChunkHash;
import mk.ripp.file.FileMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class TrackerClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final URI createUri;

    public TrackerClient(URI baseUri) {
        this.createUri = baseUri.resolve("/rips/create");
    }

    public UUID create(FileMetadata metadata) throws IOException, InterruptedException {
        CreateRip body = new CreateRip(
                metadata.getFileName(),
                metadata.getFileSize(),
                metadata.getChunkSize(),
                metadata.getChunkHashes().stream().map(FileChunkHash::getBytes).toList()
        );
        HttpRequest request = HttpRequest.newBuilder(createUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 201) {
            String message = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("Create rip failed: " + response.statusCode() + " " + message);
        }
        return json.readValue(response.body(), CreatedRip.class).id();
    }

    private record CreateRip(String fileName, long fileSize, int chunkSize, List<byte[]> chunkHashes) {}

    private record CreatedRip(UUID id, String fileName, long fileSize, int chunkSize, List<byte[]> chunkHashes) {}
}