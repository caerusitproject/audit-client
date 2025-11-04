package com.caerus.audit.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {
    private final Logger log = LoggerFactory.getLogger(HealthMonitor.class);
    private final WebSocketClient ws;
    private final UploadService uploadService;
    private final ScreenshotService screenshotService;
    private final ConfigService config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HealthMonitor(WebSocketClient ws, UploadService uploadService, ScreenshotService screenshotService, ConfigService config) {
        this.ws = ws;
        this.uploadService = uploadService;
        this.screenshotService = screenshotService;
        this.config = config;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check, 2, 2, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void check(){
        try{
            log.info("Health check: queue size etc.");
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "auditclient");
            if(Files.exists(tmp)){
                long size = Files.walk(tmp).filter(Files::isRegularFile).mapToLong(p -> {
                    try{return Files.size(p); } catch (Exception e) { return 0L;}
                }).sum();

                long mb = size / (1024 * 1024);
                var s = config.getLatest();
                int threshold = (s!=null && s.tempFolderFreeSpaceThreshold != null) ? s.tempFolderFreeSpaceThreshold : 10;
                log.info("Temp folder size: {} MB, threshold: {} MB", mb, threshold);
            }
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
        }
    }
}
