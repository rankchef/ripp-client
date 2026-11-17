package mk.ripp.peer;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCCandidatePairChangeEvent;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionIceErrorEvent;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Rtc implements AutoCloseable {
    private static final long GATHERING_TIMEOUT_SECONDS = 5;
    private static final Pattern CANDIDATE_TYPE = Pattern.compile("typ (host|srflx|prflx|relay)");

    private final PeerConnectionFactory factory = new PeerConnectionFactory();

    public Connection create(Consumer<RTCDataChannel> remoteChannels) {
        CompletableFuture<Void> gathered = new CompletableFuture<>();
        RTCPeerConnection pc = factory.createPeerConnection(config(), new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                String via = candidate.serverUrl == null ? "" : " via " + candidate.serverUrl;
                System.err.println("ICE candidate " + candidate.sdp + via);
            }

            @Override
            public void onIceCandidateError(RTCPeerConnectionIceErrorEvent event) {
                System.err.println("ICE candidate error " + event.getErrorCode() + " " + event.getUrl()
                        + " " + event.getAddress() + ":" + event.getPort() + " " + event.getErrorText());
            }

            @Override
            public void onIceGatheringChange(RTCIceGatheringState state) {
                System.err.println("ICE gathering " + state);
                if (state == RTCIceGatheringState.COMPLETE) {
                    gathered.complete(null);
                }
            }

            @Override
            public void onIceConnectionChange(RTCIceConnectionState state) {
                System.err.println("ICE connection " + state);
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                System.err.println("Peer connection " + state);
            }

            @Override
            public void onSelectedCandidatePairChanged(RTCCandidatePairChangeEvent event) {
                String local = event.getLocal() == null ? "none" : event.getLocal().sdp;
                String remote = event.getRemote() == null ? "none" : event.getRemote().sdp;
                System.err.println("ICE selected local " + local + " remote " + remote);
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
            return set.thenCompose(ignored -> gathered
                    .orTimeout(GATHERING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .handle((ok, error) -> {
                        String sdp = pc.getLocalDescription().sdp;
                        if (error == null) {
                            System.err.println("ICE gathering complete, sending " + candidates(sdp));
                        } else {
                            System.err.println("ICE gathering timed out after " + GATHERING_TIMEOUT_SECONDS
                                    + "s, sending " + candidates(sdp));
                        }
                        return sdp;
                    }));
        }

        public CompletableFuture<Void> applyRemote(RTCSdpType type, String sdp) {
            System.err.println("ICE remote description " + candidates(sdp));
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

        private static String candidates(String sdp) {
            int host = 0;
            int srflx = 0;
            int prflx = 0;
            int relay = 0;
            int other = 0;
            if (sdp != null) {
                for (String line : sdp.split("\\R")) {
                    if (!line.startsWith("a=candidate:") && !line.startsWith("candidate:")) {
                        continue;
                    }
                    Matcher type = CANDIDATE_TYPE.matcher(line);
                    if (!type.find()) {
                        other++;
                        continue;
                    }
                    switch (type.group(1)) {
                        case "host" -> host++;
                        case "srflx" -> srflx++;
                        case "prflx" -> prflx++;
                        case "relay" -> relay++;
                        default -> other++;
                    }
                }
            }
            String summary = "host " + host + ", srflx " + srflx + ", prflx " + prflx + ", relay " + relay;
            return other == 0 ? summary : summary + ", other " + other;
        }
    }

    public static String failure(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }
}