package mk.ripp.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mk.ripp.file.model.FileChunkHash;
import mk.ripp.file.model.FileMetadata;

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
    private final URI baseUri;
    private final URI createUri;

    public TrackerClient(URI baseUri) {
        this.baseUri = baseUri;
        this.createUri = baseUri.resolve("/rips/create");
    }

    public FileMetadata metadata(UUID id) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = get("/rips/" + id);
        if (response.statusCode() != 200) {
            throw new IOException("Rip metadata failed: " + response.statusCode() + " " + body(response));
        }
        CreatedRip rip = json.readValue(response.body(), CreatedRip.class);
        List<FileChunkHash> hashes = rip.chunkHashes().stream().map(FileChunkHash::new).toList();
        return new FileMetadata(rip.fileName(), rip.fileSize(), rip.chunkSize(), hashes);
    }

    public List<UUID> seeders(UUID id) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = get("/rips/" + id + "/seeders");
        if (response.statusCode() != 200) {
            throw new IOException("Seeders failed: " + response.statusCode() + " " + body(response));
        }
        return List.of(json.readValue(response.body(), UUID[].class));
    }

    private HttpResponse<byte[]> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String body(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
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