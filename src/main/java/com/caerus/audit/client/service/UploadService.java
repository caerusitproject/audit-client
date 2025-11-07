package com.caerus.audit.client.service;

import com.caerus.audit.client.queue.PersistentFileQueue;
import com.caerus.audit.client.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final PersistentFileQueue queue;
    private final WebSocketClient wsClient;
    private final HttpUtil httpUtil;

    public UploadService(PersistentFileQueue queue, WebSocketClient wsClient, HttpUtil httpUtil) {
        this.queue = queue;
        this.wsClient = wsClient;
        this.httpUtil = httpUtil;
    }

    /** Blocking sequential upload loop */
    public void start() {
        log.info("UploadService started (sequential mode)");

        // Wait until WebSocket is connected
        while (!wsClient.isConnected()) {
            log.info("Waiting for WebSocket to connect...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (true) {
            try {
                var entry = queue.peek();
                if (entry == null) {
                    Thread.sleep(2000);
                    continue;
                }

                Path file = entry.file();
                String uploadId = file.getFileName().toString();
                log.info("Uploading file [{}]...", file);

                boolean uploaded = httpUtil.uploadFile(file, uploadId);
                if (!uploaded) {
                    queue.incrementRetry(file);
                    log.warn("Upload failed for {}, will retry later.", file);
                    continue;
                }

                boolean ack = wsClient.waitForAck(uploadId, Duration.ofSeconds(20));
                if (ack) {
                    queue.markComplete(file);
                    Files.deleteIfExists(file);
                    log.info("File [{}] upload acknowledged and deleted.", file.getFileName());
                } else {
                    log.warn("No ack for {}, retrying later", uploadId);
                    queue.incrementRetry(file);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("UploadService interrupted.");
                break;
            } catch (IOException e) {
                log.error("I/O error in UploadService: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error: {}", e.getMessage());
            }
        }
    }
}