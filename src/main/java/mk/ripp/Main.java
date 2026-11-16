package mk.ripp;

import mk.ripp.file.FileSplitter;
import mk.ripp.peer.Rtc;
import mk.ripp.peer.downloader.Downloader;
import mk.ripp.peer.seeder.PeerAcceptor;
import mk.ripp.peer.seeder.RipDataSource;
import mk.ripp.peer.seeder.Seeder;
import mk.ripp.rip.RipCreator;
import mk.ripp.store.DownloadingRipStore;
import mk.ripp.store.SeedingRipStore;
import mk.ripp.tracker.TrackerClient;
import mk.ripp.tracker.TrackerSocket;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    private static final URI TRACKER = URI.create("http://34.118.24.236:7070/");

    public static void main(String[] args) throws Exception {
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Rtc rtc = new Rtc();
        Scanner in = new Scanner(System.in);
        if (args.length > 0) {
            run(args[0], desktop, rtc, in);
            return;
        }
        while (true) {
            System.out.println("1 seed");
            System.out.println("2 download");
            System.out.println("3 quit");
            System.out.print("> ");
            String choice = in.nextLine().trim();
            if (choice.equals("3") || choice.equalsIgnoreCase("quit")) {
                return;
            }
            run(choice, desktop, rtc, in);
        }
    }

    private static void run(String choice, Path desktop, Rtc rtc, Scanner in) throws Exception {
        if (choice.equals("1") || choice.equalsIgnoreCase("seed")) {
            seed(Path.of(System.getProperty("user.home"), "Downloads"), rtc, in);
        } else if (choice.equals("2") || choice.equalsIgnoreCase("download")) {
            download(desktop, rtc, in);
        } else {
            System.out.println("Choose 1 or 2");
        }
    }

    private static void seed(Path downloads, Rtc rtc, Scanner in) throws Exception {
        List<Path> files = files(downloads);
        if (files.isEmpty()) {
            System.out.println("No files in " + downloads);
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            System.out.println(i + " " + files.get(i).getFileName());
        }
        System.out.print("file number> ");
        int index = Integer.parseInt(in.nextLine().trim());
        Path file = files.get(index);

        SeedingRipStore seeding = new SeedingRipStore(Path.of("data", "seeder.db"));
        TrackerClient client = new TrackerClient(TRACKER);
        UUID ripId = new RipCreator(new FileSplitter(), client, seeding).create(file);
        System.out.println("Created rip " + ripId);

        TrackerSocket socket = new TrackerSocket(TRACKER);
        Seeder seeder = new Seeder(new RipDataSource(seeding, new FileSplitter()));
        new PeerAcceptor(socket, rtc, peer -> {
            System.out.println("Peer " + peer.peerId() + " connected for " + peer.ripId());
            seeder.serve(peer);
        });
        socket.connect();
        socket.announce(seeding.ids());
        System.out.println("Seeding " + file.getFileName() + ". Press enter to stop.");
        in.nextLine();
        socket.close();
        seeding.close();
    }

    private static void download(Path desktop, Rtc rtc, Scanner in) throws Exception {
        System.out.print("rip id> ");
        UUID ripId = UUID.fromString(in.nextLine().trim());

        SeedingRipStore seeding = new SeedingRipStore(Path.of("data", "downloader-seeding.db"));
        DownloadingRipStore downloads = new DownloadingRipStore(Path.of("data", "downloads.db"));
        TrackerClient client = new TrackerClient(TRACKER);
        TrackerSocket socket = new TrackerSocket(TRACKER);
        Downloader downloader = new Downloader(client, socket, rtc, downloads, seeding);
        socket.connect();

        System.out.println("Downloading " + ripId);
        downloader.download(ripId, desktop);
        System.out.println("Finished " + ripId + ". Press enter to stop.");
        in.nextLine();
        socket.close();
        seeding.close();
        downloads.close();
    }

    private static List<Path> files(Path desktop) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var listing = Files.list(desktop)) {
            listing.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        return files;
    }
}
