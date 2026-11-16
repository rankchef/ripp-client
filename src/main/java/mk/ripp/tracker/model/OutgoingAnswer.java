package mk.ripp.tracker.model;

import java.util.UUID;

public record OutgoingAnswer(UUID to, String sdp) implements ClientMessage {}
