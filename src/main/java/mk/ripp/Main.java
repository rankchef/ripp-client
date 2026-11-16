package mk.ripp;

import com.fasterxml.jackson.databind.ObjectMapper;
import mk.ripp.file.FileSplitter;
import mk.ripp.peer.PeerAcceptor;
import mk.ripp.peer.PeerConnector;
import mk.ripp.peer.Rtc;
import mk.ripp.rip.RipCreator;
import mk.ripp.store.RipStore;
import mk.ripp.tracker.TrackerClient;
import mk.ripp.tracker.TrackerSocket;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class Main {
    private static final URI TRACKER = URI.create("http://localhost:7070");

    public static void main(String[] args) throws Exception {
        Share share = seeder();
        downloader(share);
    }

    public static Share seeder() throws IOException, InterruptedException, ExecutionException {
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Path file = desktop.resolve("ripp-demo.txt");
        if (!Files.exists(file)) {
            Files.writeString(file, "hello from ripp seeder\n");
        }

        RipStore rips = new RipStore(Path.of("data", "seeder.db"));
        TrackerClient client = new TrackerClient(TRACKER);
        RipCreator creator = new RipCreator(new FileSplitter(), client, rips);
        UUID ripId = creator.create(file);
        System.out.println("Seeder: created rip " + ripId + " from " + file);

        TrackerSocket socket = new TrackerSocket(TRACKER);
        Rtc rtc = new Rtc();
        new PeerAcceptor(socket, rtc, peer ->
                System.out.println("Seeder: peer " + peer.peerId()
                        + " connected for rip " + peer.ripId()));
        socket.connect();
        socket.announce(List.of(ripId));
        System.out.println("Seeder: announced rip " + ripId);

        UUID seederId = ownSeederId(ripId);
        System.out.println("Seeder: peer id " + seederId);
        return new Share(seederId, ripId, socket, rtc, rips);
    }

    public static void downloader(Share share) throws InterruptedException, ExecutionException {
        TrackerSocket socket = new TrackerSocket(TRACKER);
        Rtc rtc = new Rtc();
        PeerConnector connector = new PeerConnector(socket, rtc);
        socket.connect();
        System.out.println("Downloader: connecting to seeder " + share.seederId()
                + " for rip " + share.ripId());

        connector.connect(share.seederId(), share.ripId())
                .whenComplete((peer, error) -> {
                    if (error != null) {
                        System.err.println("Downloader: connect failed: " + error.getMessage());
                        return;
                    }
                    System.out.println("Downloader: connected to " + peer.peerId()
                            + " for rip " + peer.ripId());
                })
                .join();
    }

    private static UUID ownSeederId(UUID ripId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(TRACKER.resolve("/rips/" + ripId + "/seeders"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Could not list seeders: " + response.statusCode() + " " + response.body());
        }
        UUID[] seeders = new ObjectMapper().readValue(response.body(), UUID[].class);
        if (seeders.length == 0) {
            throw new IllegalStateException("No seeders announced for rip " + ripId);
        }
        return seeders[0];
    }

    public record Share(UUID seederId, UUID ripId, TrackerSocket socket, Rtc rtc, RipStore rips) {}
}
