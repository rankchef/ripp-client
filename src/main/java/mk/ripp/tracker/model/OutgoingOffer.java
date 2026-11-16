package mk.ripp.tracker.model;

import java.util.UUID;

public record OutgoingOffer(UUID to, UUID ripId, String sdp) implements ClientMessage {}
