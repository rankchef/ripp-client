package mk.ripp.peer.seeder;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCSdpType;
import mk.ripp.peer.PeerChannel;
import mk.ripp.peer.Rtc;
import mk.ripp.tracker.TrackerSocket;
import mk.ripp.tracker.model.IncomingOffer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PeerAcceptor {
    private static final long CONNECT_TIMEOUT_SECONDS = 30;

    private final TrackerSocket tracker;
    private final Rtc rtc;
    private final Consumer<PeerChannel> uploads;

    public PeerAcceptor(TrackerSocket tracker, Rtc rtc, Consumer<PeerChannel> uploads) {
        this.tracker = tracker;
        this.rtc = rtc;
        this.uploads = uploads;
        tracker.onOffer(this::onOffer);
    }

    public void onOffer(IncomingOffer offer) {
        CompletableFuture<RTCDataChannel> arrived = new CompletableFuture<>();
        Rtc.Connection connection = rtc.create(arrived::complete);

        connection.applyRemote(RTCSdpType.OFFER, offer.sdp())
                .thenCompose(ignored -> connection.localSdp())
                .thenAcceptAsync(sdp -> tracker.answer(offer.from(), sdp))
                .exceptionally(error -> {
                    arrived.completeExceptionally(error);
                    return null;
                });

        arrived.orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((channel, error) -> {
            if (error != null) {
                connection.close();
                System.err.println("Could not connect to " + offer.from() + ": " + error.getMessage());
                return;
            }
            uploads.accept(new PeerChannel(offer.from(), offer.ripId(), channel, connection.pc()));
        });
    }
}