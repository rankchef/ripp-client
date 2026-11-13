package mk.ripp;

import mk.ripp.file.FileSplitter;
import mk.ripp.rip.RipCreator;
import mk.ripp.store.RipStore;
import mk.ripp.tracker.TrackerClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: Main <file>");
            return;
        }
        Path file = Path.of(args[0]);
        try (RipStore rips = new RipStore(Path.of("data", "ripp.db"))) {
            TrackerClient client = new TrackerClient(URI.create("http://localhost:7070"));
            RipCreator creator = new RipCreator(new FileSplitter(), client, rips);
            UUID id = creator.create(file);
            System.out.println(id);
        }
    }
}