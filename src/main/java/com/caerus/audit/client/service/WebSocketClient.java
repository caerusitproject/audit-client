package com.caerus.audit.client.service;

import com.caerus.audit.client.model.ServerAppSettingsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
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
                            log.info("WS closed {} {}", statusCode, reason);
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
        if(msg.startsWith("UPLOAD_SUCCESS:")){
            String nameOrPath = msg.substring("UPLOAD_SUCCESS:".length());
            String fileName = nameOrPath.replace("\\", "/");
            int idx = fileName.lastIndexOf('/');
            if(idx >= 0) fileName = fileName.substring(idx+1);
            CompletableFuture<Boolean> f = ackMap.remove(fileName);
            if(f!=null) f.complete(true);
        } else if(msg.startsWith("UPLOAD_FAILED:")){
            String fileName = msg.substring("UPLOAD_FAILED:".length()).trim();
            CompletableFuture<Boolean> f = ackMap.remove(fileName);
            if(f!=null) f.complete(false);
        } else if("ping".equalsIgnoreCase(msg.trim())){
            log.info("server ping");
        } else{
            log.info("WS message: {}", msg);
        }
    }

    public boolean waitForAck(String fileName, long timeoutSeconds){
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        ackMap.put(fileName, f);
        try{
            return f.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            ackMap.remove(fileName);
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

}
