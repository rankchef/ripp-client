package mk.ripp.peer.seeder;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import mk.ripp.peer.PeerChannel;
import mk.ripp.peer.RipDataCodec;
import mk.ripp.peer.model.RipDataRequest;
import mk.ripp.peer.model.RipDataResponse;

import java.nio.ByteBuffer;

public final class Seeder {
    private final RipDataSource data;

    public Seeder(RipDataSource data) {
        this.data = data;
    }

    public void serve(PeerChannel peer) {
        RTCDataChannel channel = peer.channel();
        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
            }

            @Override
            public void onStateChange() {
                if (channel.getState() == RTCDataChannelState.CLOSED) {
                    peer.close();
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                try {
                    RipDataRequest request = RipDataCodec.decodeRequest(buffer.data);
                    RipDataResponse response = data.read(request.ripId(), request.sequence());
                    for (ByteBuffer frame : RipDataCodec.encodeResponse(response)) {
                        channel.send(new RTCDataChannelBuffer(frame, true));
                    }
                } catch (Exception e) {
                    System.err.println("Seeder: could not serve " + peer.peerId() + ": " + e.getMessage());
                }
            }
        });
    }
}
