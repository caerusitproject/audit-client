package com.caerus.audit.client.service;

import com.caerus.audit.client.model.ServerAppSettingsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

public class WebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);

    private final String serverBase;
    private final String clientId;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Boolean> ackMap = new ConcurrentHashMap<>();

    private volatile WebSocket ws;
    private volatile boolean connected = false;

    public WebSocketClient(String serverBase, String clientId) {
        this.serverBase = serverBase;
        this.clientId = clientId;
    }

    public synchronized void start() {
        if (connected) return;

        try {
            String uri = serverBase.replaceFirst("^http", "ws") + "/ws/heartbeat?clientId=" + clientId;
            log.info("Connecting WebSocket: {}", uri);

            ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(uri), new WSListener())
                    .join();

            connected = true;
        } catch (Exception e) {
            log.error("WebSocket connection failed: {}", e.getMessage(), e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public synchronized void stop() {
        try {
            if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed").join();
        } catch (Exception ignored) {}
        connected = false;
    }

    public synchronized void sendText(String text) {
        if (ws != null && connected) {
            try {
                ws.sendText(text, true);
            } catch (Exception e) {
                log.error("WS send error: {}", e.getMessage());
            }
        }
    }

    public boolean waitForAck(String uploadId, Duration timeout) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            Boolean ack = ackMap.remove(uploadId);
            if (ack != null) return ack;
            Thread.sleep(200);
        }
        return false;
    }

    public void sendPong() {
        sendText("pong");
        log.debug("Sent pong heartbeat to server.");
    }

    private class WSListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("WebSocket connected successfully.");
            connected = true;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = data.toString();
            log.info("WS msg: {}", message);

            if ("ping".equalsIgnoreCase(message)) {
                log.debug("Received ping, sending pong...");
                sendPong();
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            } else if ("pong".equalsIgnoreCase(message)) {
                log.debug("Received pong from server.");
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            try {
                JsonNode node = mapper.readTree(message);
                String type = node.path("type").asText();
                String uploadId = node.path("uploadId").asText();
                boolean success = node.path("success").asBoolean(true);

                if (type.startsWith("UPLOAD_SUCCESS")) {
                    ackMap.put(uploadId, success);
                    log.info("Ack received for uploadId={} success={}", uploadId, success);
                }
            } catch (Exception e) {
                log.error("Invalid WS message: {}", e.getMessage());
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            log.warn("WebSocket closed [{}]: {}", statusCode, reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            log.error("WebSocket error: {}", error.getMessage(), error);
        }
    }
}