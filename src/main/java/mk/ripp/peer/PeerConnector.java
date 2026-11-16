package mk.ripp.peer;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import dev.onvoid.webrtc.RTCSdpType;
import mk.ripp.tracker.TrackerSocket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import mk.ripp.tracker.model.IncomingAnswer;

public final class PeerConnector {
    private static final long CONNECT_TIMEOUT_SECONDS = 30;

    private final TrackerSocket tracker;
    private final Rtc rtc;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PeerConnector(TrackerSocket tracker, Rtc rtc) {
        this.tracker = tracker;
        this.rtc = rtc;
        tracker.onAnswer(this::onAnswer);
    }

    public CompletableFuture<PeerChannel> connect(UUID seederId, UUID ripId) {
        Rtc.Connection connection = rtc.create(ignored -> {});
        RTCDataChannel channel = connection.pc().createDataChannel(ripId.toString(), new RTCDataChannelInit());
        Pending request = new Pending(connection, new CompletableFuture<>());
        if (pending.putIfAbsent(seederId, request) != null) {
            channel.dispose();
            connection.close();
            return CompletableFuture.failedFuture(new IllegalStateException("Already connecting to " + seederId));
        }
        CompletableFuture<PeerChannel> result = request.result();

        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long sentDataSize) {
            }

            @Override
            public void onStateChange() {
                if (channel.getState() == RTCDataChannelState.OPEN) {
                    result.complete(new PeerChannel(seederId, ripId, channel, connection.pc()));
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
            }
        });

        connection.localSdp()
                .thenAcceptAsync(sdp -> tracker.offer(seederId, ripId, sdp))
                .exceptionally(error -> {
                    result.completeExceptionally(error);
                    return null;
                });

        result.orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((open, error) -> {
            pending.remove(seederId, request);
            if (error != null) {
                channel.close();
                channel.dispose();
                connection.close();
            }
        });
        return result;
    }

    public void onAnswer(IncomingAnswer answer) {
        Pending request = pending.get(answer.from());
        if (request == null) {
            System.err.println("No offer waiting for " + answer.from());
            return;
        }
        request.connection().applyRemote(RTCSdpType.ANSWER, answer.sdp())
                .exceptionally(error -> {
                    request.result().completeExceptionally(error);
                    return null;
                });
    }

    private record Pending(Rtc.Connection connection, CompletableFuture<PeerChannel> result) {}
}