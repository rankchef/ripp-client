package mk.ripp.peer;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCPeerConnection;

import java.util.UUID;

public record PeerChannel(UUID peerId, UUID ripId, RTCDataChannel channel, RTCPeerConnection connection) {
    public void close() {
        channel.close();
        channel.dispose();
        connection.close();
    }
}