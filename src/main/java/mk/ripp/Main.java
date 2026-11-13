package mk.ripp;

import mk.ripp.file.FileSplitter;
import mk.ripp.rip.RipCreator;
import mk.ripp.store.RipStore;
import mk.ripp.tracker.TrackerClient;
import mk.ripp.tracker.TrackerSocket;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
            RipStore rips = new RipStore(Path.of("data", "ripp.db"));
            TrackerClient client = new TrackerClient(URI.create("http://localhost:7070"));
            RipCreator creator = new RipCreator(new FileSplitter(), client, rips);
            TrackerSocket socket = new TrackerSocket(URI.create("http://localhost:7070"));
    }
}