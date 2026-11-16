package mk.ripp.tracker.model;

import java.util.UUID;

public record IncomingOffer(UUID from, UUID ripId, String sdp) {
    public IncomingOffer {
        if (from == null || ripId == null || sdp == null || sdp.isBlank()) {
            throw new IllegalArgumentException("offer requires from, ripId and sdp");
        }
    }
}