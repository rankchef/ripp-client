package mk.ripp.peer.model;

import java.util.UUID;

public record RipDataResponse(UUID ripId, int sequence, RipDataStatus status, byte[] data) {
    public RipDataResponse {
        if (ripId == null || status == null) {
            throw new IllegalArgumentException("ripId and status are required");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        if (status == RipDataStatus.OK) {
            if (data == null) {
                throw new IllegalArgumentException("OK response requires data");
            }
        } else if (data != null) {
            throw new IllegalArgumentException("non-OK response must not include data");
        }
    }
}
