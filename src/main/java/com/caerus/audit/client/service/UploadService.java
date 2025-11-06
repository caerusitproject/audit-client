package com.caerus.audit.client.service;

import com.caerus.audit.client.queue.PersistentFileQueue;
import com.caerus.audit.client.util.HttpUtil;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService implements Runnable{
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final PersistentFileQueue queue;
    private final WebSocketClient wsClient;
    private final HttpUtil httpUtil;
    private volatile boolean running = true;

    public UploadService(PersistentFileQueue queue, WebSocketClient wsClient, HttpUtil httpUtil) {
        this.queue = queue;
        this.wsClient = wsClient;
        this.httpUtil = httpUtil;
    }

    @Override
    public void run() {
        log.info("UploadService started.");

        while (running) {
            try {
                Path file = queue.take();
                String uploadId = UUID.randomUUID().toString();
                log.info("Uploading file [{}] with uploadId={}", file.getFileName(), uploadId);

                wsClient.prepareAck(uploadId);

                boolean uploaded = httpUtil.uploadFile(file, uploadId);
                if (!uploaded) {
                    log.warn("Upload failed for: {}, will retry later.", file);
                    wsClient.cancelAck(uploadId);
                    queue.enqueue(file);
                    Thread.sleep(5000);
                    continue;
                }

                log.info("Upload sent successfully, waiting for server acknowledgment...");
                boolean ack = wsClient.waitForAck(uploadId, Duration.ofSeconds(30));

                if (ack) {
                   try{
                       queue.markComplete(file);
                       Files.deleteIfExists(file);
                       log.info("File [{}] acknowledged and deleted.", file.getFileName());
                   } catch (IOException e) {
                       log.error("Failed to delete {} after acknowledgment: {}", file, e.getMessage());
                   }
                } else {
                    log.warn("No acknowledgment for uploadId={}, re-enqueuing file{}", uploadId, file);
                    queue.enqueue(file);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("UploadService interrupted.");
                break;
            } catch (IOException e) {
                log.error("I/O error in UploadService", e);
            } catch (Exception e) {
                log.error("Unexpected error in UploadService", e);
            }
        }

        log.info("UploadService stopped.");
    }

    public void stop() {
        this.running = false;
    }
}
