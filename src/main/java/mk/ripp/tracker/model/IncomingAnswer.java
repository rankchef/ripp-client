package mk.ripp.tracker.model;

import java.util.UUID;

public record IncomingAnswer(UUID from, String sdp) {
    public IncomingAnswer {
        if (from == null || sdp == null || sdp.isBlank()) {
            throw new IllegalArgumentException("answer requires from and sdp");
        }
    }
}