package mk.ripp.tracker.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Announce.class, name = "announce"),
        @JsonSubTypes.Type(value = OutgoingOffer.class, name = "offer"),
        @JsonSubTypes.Type(value = OutgoingAnswer.class, name = "answer")
})
public sealed interface ClientMessage permits Announce, OutgoingOffer, OutgoingAnswer {}