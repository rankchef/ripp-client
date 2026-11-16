package mk.ripp.peer.downloader;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import mk.ripp.file.model.FileMetadata;
import mk.ripp.peer.PeerChannel;
import mk.ripp.peer.RipDataCodec;
import mk.ripp.peer.Rtc;
import mk.ripp.peer.model.RipDataRequest;
import mk.ripp.peer.model.RipDataResponse;
import mk.ripp.peer.model.RipDataStatus;
import mk.ripp.store.DownloadingRipStore;
import mk.ripp.store.SeedingRipStore;
import mk.ripp.tracker.TrackerClient;
import mk.ripp.tracker.TrackerSocket;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Downloader {
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final TrackerClient tracker;
    private final TrackerSocket socket;
    private final PeerConnector peers;
    private final DownloadingRipStore downloads;
    private final SeedingRipStore seeding;
    private final RipDataWriter writer;

    public Downloader(
            TrackerClient tracker,
            TrackerSocket socket,
            Rtc rtc,
            DownloadingRipStore downloads,
            SeedingRipStore seeding
    ) {
        this.tracker = tracker;
        this.socket = socket;
        this.peers = new PeerConnector(socket, rtc);
        this.downloads = downloads;
        this.seeding = seeding;
        this.writer = new RipDataWriter(downloads);
    }

    public void download(UUID ripId, Path directory) throws Exception {
        FileMetadata metadata = tracker.metadata(ripId);
        Path file = directory.resolve(metadata.getFileName());
        DownloadingRipStore.Download download = downloads.start(ripId, file, metadata);
        if (download.done()) {
            finish(download);
            return;
        }

        List<UUID> seeders = tracker.seeders(ripId);
        if (seeders.isEmpty()) {
            throw new IllegalStateException("No seeders for rip " + ripId);
        }

        int index = 0;
        int failures = 0;
        while (!fresh(ripId).done()) {
            UUID seederId = seeders.get(index % seeders.size());
            try {
                PeerChannel peer = peers.connect(seederId, ripId).join();
                boolean progress;
                try {
                    progress = pull(peer, ripId);
                } finally {
                    peer.close();
                }
                if (fresh(ripId).done()) {
                    break;
                }
                if (!progress) {
                    failures++;
                    if (failures >= seeders.size()) {
                        throw new IllegalStateException("All seeders failed for rip " + ripId);
                    }
                } else {
                    failures = 0;
                }
                index++;
            } catch (Exception e) {
                index++;
                failures++;
                if (failures >= seeders.size()) {
                    throw new IllegalStateException("All seeders failed for rip " + ripId, e);
                }
            }
        }
        finish(fresh(ripId));
    }

    private boolean pull(PeerChannel peer, UUID ripId) throws Exception {
        boolean progress = false;
        for (int sequence : fresh(ripId).missing()) {
            RipDataResponse response = request(peer, new RipDataRequest(ripId, sequence));
            if (response.status() != RipDataStatus.OK || !response.ripId().equals(ripId) || response.sequence() != sequence) {
                return progress;
            }
            if (!writer.write(response)) {
                return progress;
            }
            progress = true;
        }
        return true;
    }

    private RipDataResponse request(PeerChannel peer, RipDataRequest request) throws Exception {
        CompletableFuture<RipDataResponse> reply = new CompletableFuture<>();
        RipDataCodec.Assembler frames = new RipDataCodec.Assembler();
        RTCDataChannel channel = peer.channel();
        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
            }

            @Override
            public void onStateChange() {
                if (channel.getState() == RTCDataChannelState.CLOSED && !reply.isDone()) {
                    reply.completeExceptionally(new IllegalStateException("Channel closed"));
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                try {
                    RipDataResponse response = frames.add(buffer.data);
                    if (response != null) {
                        reply.complete(response);
                    }
                } catch (RuntimeException e) {
                    reply.completeExceptionally(e);
                }
            }
        });
        channel.send(new RTCDataChannelBuffer(RipDataCodec.encodeRequest(request), true));
        return reply.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
    }

    private void finish(DownloadingRipStore.Download download) {
        if (seeding.find(download.id()).isEmpty()) {
            seeding.insert(download.id(), download.file(), download.metadata());
        }
        downloads.remove(download.id());
        socket.announce(seeding.ids());
    }

    private DownloadingRipStore.Download fresh(UUID ripId) {
        return downloads.find(ripId).orElseThrow(() -> new IllegalStateException("No download " + ripId));
    }
}
