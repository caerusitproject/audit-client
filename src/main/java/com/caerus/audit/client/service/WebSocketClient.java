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
import java.util.concurrent.*;

public class WebSocketClient {
    private final Logger log = LoggerFactory.getLogger(WebSocketClient.class);
    private final String serverBase;
    private final String clientId;
    private final ConfigService config;
    private volatile WebSocket ws;
    private ScheduledExecutorService pingScheduler;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();

    private final ConcurrentMap<String, CompletableFuture<Boolean>> ackMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketClient(String serverBase, String clientId, ConfigService config) {
        this.serverBase = serverBase;
        this.clientId = clientId;
        this.config = config;
    }

    public void start(){
        connect();

        pingScheduler = Executors.newSingleThreadScheduledExecutor();
        pingScheduler.scheduleAtFixedRate(this::sendPong, 1, 1, TimeUnit.SECONDS);
    }

    public void stop(){
        try{
            if(ws!=null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed").join();
            } catch (Exception e) {}
        if(pingScheduler !=null) pingScheduler.shutdownNow();
    }

    private void connect(){
        try{
            String uri = serverBase.replaceFirst("^http", "ws") + "/ws/heartbeat?clientId=" + clientId;
            ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(uri), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                           String s = data.toString();
                           handleMessage(s);
                           webSocket.request(1);
                           return null;
                        }

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("WS connected");
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("WS closed [{}] {}", statusCode, reason);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                           log.error("WS error", error.getMessage());
                        }
                    }).join();
        } catch (Exception e) {
            log.error("Failed to connect WS: {}", e.getMessage());
        }
    }

    private void sendPong(){
        try{
            ServerAppSettingsDto s = config.getLatest();
            long interval = (s!=null && s.configHeartbeatInterval!=null) ? s.configHeartbeatInterval : 1;
            if(ws == null) connect();
            if(ws != null) ws.sendText("pong", true);
        } catch (Exception e) {
            log.error("Pong failed: {}", e.getMessage());
        }
    }

    private void handleMessage(String msg){
        log.info("WS msg: {}", msg);
        String trimmed = msg.trim();

        if ("ping".equalsIgnoreCase(trimmed)) {
            log.debug("Server heartbeat ping received");
            sendText("pong");
            return;
        }

        if ("pong".equalsIgnoreCase(trimmed)) {
            log.debug("Server acknowledged pong");
            return;
        }

        try{
            JsonNode node = objectMapper.readTree(trimmed);
            String type = node.path("type").asText();

            if ("UPLOAD_SUCCESS".equalsIgnoreCase(type) || "UPLOAD_FAILED".equalsIgnoreCase(type)) {
                String uploadId = node.path("uploadId").asText();
                boolean success = node.path("success").asBoolean(true);

                CompletableFuture<Boolean> future = ackMap.remove(uploadId);
                if (future != null) {
                    future.complete(success);
                    log.info("Ack received for uploadId={} (success={})", uploadId, success);
                } else {
                    log.warn("No pending upload waiting for ack uploadId={}", uploadId);
                }
            }
        } catch (Exception e) {
            log.error("Non-JSON or invalid WS message: {}", trimmed);
        }
    }

    public boolean waitForAck(String uploadId, Duration timeout) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ackMap.put(uploadId, future);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for ack of uploadId={}", uploadId);
            ackMap.remove(uploadId);
            return false;
        } catch (Exception e) {
            log.error("Error waiting for ack of {}: {}", uploadId, e.getMessage());
            ackMap.remove(uploadId);
            return false;
        }
    }

    public void sendText(String txt){
        if(ws != null){
            try{
                ws.sendText(txt, true);
            } catch (Exception e) {
                log.error("WS send failed: {}", e.getMessage());
            }
        }
    }

    public void prepareAck(String uploadId){
        ackMap.put(uploadId, new CompletableFuture<>());
    }

    public void cancelAck(String uploadId){
        ackMap.remove(uploadId);
    }
}
