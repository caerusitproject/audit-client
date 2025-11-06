package com.caerus.audit.client.service;

import com.caerus.audit.client.queue.PersistentFileQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScreenshotService {
    private final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private final ConfigService config;
    private final PersistentFileQueue queue;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Robot robot;
    private volatile boolean running = false;

    private static final long MAX_FOLDER_SIZE_MB = 10;

    public ScreenshotService(ConfigService config, PersistentFileQueue queue) {
        this.config = config;
        this.queue = queue;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            log.error("Failed to create Robot", e);
            throw new IllegalStateException(e);
        }
    }

    public void start(){
        running = true;
        long intervalSec = Math.max(1, getCaptureInterval());
        scheduler.scheduleAtFixedRate(this::captureIfActive, 0, intervalSec, TimeUnit.SECONDS);
    }

    public void stop(){
        running = false;
        scheduler.shutdown();
    }

    private boolean hasSpace(Path tmpDir){
        try{
            long size = Files.walk(tmpDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();

            long sizeMb = size / (1024 * 1024);
            return sizeMb < MAX_FOLDER_SIZE_MB;
        } catch (IOException e){
            log.error("Error checking folder size", e);
            return true;
        }
    }

    private int getCaptureInterval() {
        var s = config.getLatest();
        return (s != null && s.configCaptureInterval != null) ? s.configCaptureInterval : 3;
    }

    private void captureIfActive() {
        try{
            if(!running) return;
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "auditclient");

            if(!hasSpace(tmpDir)){
                log.warn("Temp folder reached limit (>{} MB), pausing captures", MAX_FOLDER_SIZE_MB);
                stop(); // Stop capturing temporarily
                return;
            }
            capture();
        } catch (Exception e) {
            log.error("Capture error: {}", e.getMessage());
        }
    }

    public Path capture() throws Exception{
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "auditclient");
        Files.createDirectories(tmpDir);
        Path out = tmpDir.resolve(timestamp + ".png");

        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = robot.createScreenCapture(screenRect);
        ImageIO.write(img, "png", out.toFile());
        queue.enqueue(out);
        log.info("Captured screenshot to {}", out);
        return out;
    }
}
