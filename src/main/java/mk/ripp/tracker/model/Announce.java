package mk.ripp.tracker.model;

import java.util.List;
import java.util.UUID;

public record Announce(List<UUID> rips) implements ClientMessage {}