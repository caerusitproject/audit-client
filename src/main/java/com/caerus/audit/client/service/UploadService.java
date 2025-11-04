package com.caerus.audit.client.service;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService {
    private final Logger log = LoggerFactory.getLogger(UploadService.class);
    private final String serverBase;
    private final String clientId;
    private final FileQueue queue;
    private final WebSocketClient ws;
    private final ConfigService config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    public UploadService(String serverBase, String clientId, FileQueue queue, WebSocketClient ws, ConfigService config) {
        this.serverBase = serverBase;
        this.clientId = clientId;
        this.queue = queue;
        this.ws = ws;
        this.config = config;
    }

    public void start(){
        running = true;
        worker.submit(this::processLoop);
    }

    public void stop(){
        running = false;
        worker.shutdownNow();
    }

    private void processLoop(){
        try(CloseableHttpClient http = HttpClients.createDefault()){
            while (running){
                Path p = queue.take();
                int attempts = 0;
                boolean done = false;
                while(!done && attempts < 5){
                    attempts++;
                    try{
                        HttpPost post = new HttpPost(serverBase + "/api/v1/upload");
                        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                        builder.addBinaryBody("file", p.toFile(), ContentType.create("image/png"), p.getFileName().toString());
                        builder.addTextBody("clientId", clientId);
                        post.setEntity(builder.build());
                        var resp = http.execute(post);
                        int sc = resp.getCode();
                        resp.close();
                        if(sc >= 200 && sc < 300){
                            log.info("Uploaded {} -> server (attempt {})", p, attempts);
                            boolean ack = ws.waitForAck(p.getFileName().toString(), Math.max(5, getCommResolveWindow()));
                            if(ack){
                                Files.deleteIfExists(p);
                                done = true;
                            } else {
                                log.warn("Server did not acknowledge upload of {} (attempt {})", p, attempts);
                            }
                        } else {
                            log.warn("Server returned {} (attempt {})", sc, attempts);
                        }
                    } catch (Exception e) {
                        log.error("Error uploading {}", p, e);
                        Thread.sleep(2000L * attempts);
                    }
                }
                if(!done){
                    log.error("Failed to upload {} after 5 attempts", p);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Upload service interrupted");
        } catch (Exception e) {
            log.error("Upload service error: {}", e.getMessage(), e);
        }
    }

    private int getCommResolveWindow(){
        var s = config.getLatest();
        return (s!=null && s.configCommIssueAutoResolveWindow != null) ? s.configCommIssueAutoResolveWindow : 10;
    }
}
