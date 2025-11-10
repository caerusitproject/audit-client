package com.caerus.audit.client.service;

import com.caerus.audit.client.queue.PersistentFileQueue;
import com.caerus.audit.client.util.SystemLock;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScreenshotService {
    private final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private static final long MAX_FOLDER_SIZE_MB = 10;
    private static final double LOCK_THRESHOLD = 0.9;
    private static final double UNLOCK_THRESHOLD = 0.5;

    private final ConfigService config;
    private final PersistentFileQueue queue;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> captureTask;
    private final Object lock = new Object();
    private Robot robot;
    private volatile boolean running = false;
    private volatile boolean lockedDueToSpace = false;

    public ScreenshotService(ConfigService config, PersistentFileQueue queue) {
        this.config = config;
        this.queue = queue;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            log.error("Failed to create Robot", e);
            throw new IllegalStateException(e);
        }
        this.scheduler = createScheduler();
    }

    private ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ScreenshotService-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(){
        synchronized (lock) {
            if (running) {
                log.debug("ScreenshotService already running");
                return;
            }
            running = true;

            if (scheduler.isShutdown() || scheduler.isTerminated()) {
                log.warn("Scheduler was shut down — recreating executor");
                scheduler = createScheduler();
            }

            long intervalSec = Math.max(1, getCaptureInterval());
            captureTask = scheduler.scheduleAtFixedRate(
                    this::captureIfActive, 0, intervalSec, TimeUnit.SECONDS
            );
            log.info("ScreenshotService started (interval={}s)", intervalSec);
        }
    }

    public void stop(){
        synchronized (lock) {
            if (!running) return;
            running = false;

            if (captureTask != null && !captureTask.isCancelled()) {
                captureTask.cancel(false);
                captureTask = null;
            }

            log.info("ScreenshotService paused (scheduler still alive)");
        }
    }

    private double getUsedFraction(Path tmpDir) {
        try {
            long totalBytes = MAX_FOLDER_SIZE_MB * 1024 * 1024;
            long usedBytes = Files.walk(tmpDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();

            return (double) usedBytes / totalBytes;
        } catch (IOException e) {
            log.error("Error calculating folder usage", e);
            return 0.0;
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

            double usedFraction = getUsedFraction(tmpDir);
            if(lockedDueToSpace){
               if(usedFraction <= UNLOCK_THRESHOLD){
                   lockedDueToSpace = false;
                   log.info("Disk usage below 50%, resuming screenshot capture...");
                   start();
               }else {
                   log.info("Still waiting for disk cleanup (used {:.2f}%)", usedFraction * 100);
                   return;
               }
            }
            if(usedFraction >= LOCK_THRESHOLD){
                lockedDueToSpace = true;
                stop();
                log.warn("Disk usage above 90%, pausing screenshot capture...");
                SystemLock.lockWorkstation(); // todo: log to db here
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
