package mk.ripp.peer.model;

import java.util.UUID;

/** Ask a seeder for one piece of a rip. */
public record RipDataRequest(UUID ripId, int sequence) {
    public RipDataRequest {
        if (ripId == null) {
            throw new IllegalArgumentException("ripId is required");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
    }
}