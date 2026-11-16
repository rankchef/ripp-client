package mk.ripp.tracker;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mk.ripp.tracker.model.Announce;
import mk.ripp.tracker.model.ClientMessage;
import mk.ripp.tracker.model.IncomingAnswer;
import mk.ripp.tracker.model.IncomingOffer;
import mk.ripp.tracker.model.OutgoingAnswer;
import mk.ripp.tracker.model.OutgoingOffer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class TrackerSocket implements WebSocket.Listener {
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final StringBuilder incoming = new StringBuilder();
    private final URI wsUri;
    private volatile WebSocket socket;
    private volatile Consumer<IncomingOffer> offers;
    private volatile Consumer<IncomingAnswer> answers;

    public TrackerSocket(URI baseUri) {
        this.wsUri = URI.create(baseUri.toString().replaceFirst("^http", "ws") + "/ws");
    }

    public void onOffer(Consumer<IncomingOffer> offers) {
        this.offers = offers;
    }

    public void onAnswer(Consumer<IncomingAnswer> answers) {
        this.answers = answers;
    }

    public void connect() throws InterruptedException, ExecutionException {
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(wsUri, this)
                .get();
    }

    public void announce(List<UUID> rips) {
        send(new Announce(rips));
    }

    public void offer(UUID to, UUID ripId, String sdp) {
        send(new OutgoingOffer(to, ripId, sdp));
    }

    public void answer(UUID to, String sdp) {
        send(new OutgoingAnswer(to, sdp));
    }

    private void send(ClientMessage message) {
        try {
            String text = json.writerFor(ClientMessage.class).writeValueAsString(message);
            socket.sendText(text, true).join();
        } catch (Exception e) {
            throw new IllegalStateException("Could not send " + message.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incoming.append(data);
        if (last) {
            String text = incoming.toString();
            incoming.setLength(0);
            onMessage(text);
        }
        webSocket.request(1);
        return null;
    }

    private void onMessage(String text) {
        try {
            JsonNode message = json.readTree(text);
            switch (message.path("type").asText()) {
                case "offer" -> onIncomingOffer(message);
                case "answer" -> onIncomingAnswer(message);
                default -> System.out.println(text);
            }
        } catch (Exception e) {
            System.err.println("Could not handle tracker message " + text + ": " + e.getMessage());
        }
    }

    private void onIncomingOffer(JsonNode message) throws Exception {
        offers.accept(json.treeToValue(message, IncomingOffer.class));
    }

    private void onIncomingAnswer(JsonNode message) throws Exception {
        answers.accept(json.treeToValue(message, IncomingAnswer.class));
    }
}