package mk.ripp.peer;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Rtc implements AutoCloseable {
    private static final long GATHERING_TIMEOUT_SECONDS = 5;

    private final PeerConnectionFactory factory = new PeerConnectionFactory();

    public Connection create(Consumer<RTCDataChannel> remoteChannels) {
        CompletableFuture<Void> gathered = new CompletableFuture<>();
        RTCPeerConnection pc = factory.createPeerConnection(config(), new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
            }

            @Override
            public void onIceGatheringChange(RTCIceGatheringState state) {
                if (state == RTCIceGatheringState.COMPLETE) {
                    gathered.complete(null);
                }
            }

            @Override
            public void onDataChannel(RTCDataChannel channel) {
                remoteChannels.accept(channel);
            }
        });
        return new Connection(pc, gathered);
    }

    @Override
    public void close() {
        factory.dispose();
    }

    private static RTCConfiguration config() {
        RTCConfiguration config = new RTCConfiguration();
        RTCIceServer stun = new RTCIceServer();
        stun.urls.add("stun:stun.l.google.com:19302");
        config.iceServers.add(stun);
        return config;
    }

    public record Connection(RTCPeerConnection pc, CompletableFuture<Void> gathered) {
        public CompletableFuture<String> localSdp() {
            CompletableFuture<Void> set = new CompletableFuture<>();
            pc.setLocalDescription(observer(set));
            return set
                    .thenCompose(ignored -> gathered.completeOnTimeout(null, GATHERING_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .thenApply(ignored -> pc.getLocalDescription().sdp);
        }

        public CompletableFuture<Void> applyRemote(RTCSdpType type, String sdp) {
            CompletableFuture<Void> set = new CompletableFuture<>();
            pc.setRemoteDescription(new RTCSessionDescription(type, sdp), observer(set));
            return set;
        }

        public void close() {
            pc.close();
        }

        private static SetSessionDescriptionObserver observer(CompletableFuture<Void> done) {
            return new SetSessionDescriptionObserver() {
                @Override
                public void onSuccess() {
                    done.complete(null);
                }

                @Override
                public void onFailure(String error) {
                    done.completeExceptionally(new IllegalStateException(error));
                }
            };
        }
    }
}