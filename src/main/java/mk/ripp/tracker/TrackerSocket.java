package mk.ripp.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class TrackerSocket implements WebSocket.Listener {
    private final ObjectMapper json = new ObjectMapper();
    private final WebSocket socket;

    public TrackerSocket(URI baseUri) throws InterruptedException, ExecutionException {
        URI ws = URI.create(baseUri.toString().replaceFirst("^http", "ws") + "/ws");
        socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(ws, this)
                .get();
    }

    public void announce(List<UUID> rips) {
        try {
            String message = json.writerFor(ClientMessage.class).writeValueAsString(new Announce(rips));
            socket.sendText(message, true).join();
        } catch (Exception e) {
            throw new IllegalStateException("Could not announce rips", e);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        System.out.println(data);
        webSocket.request(1);
        return null;
    }
}