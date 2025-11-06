package com.caerus.audit.client;

import com.caerus.audit.client.queue.PersistentFileQueue;
import com.caerus.audit.client.service.*;
import com.caerus.audit.client.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App
{
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main( String[] args ) throws Exception{
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                log.error("Unhandled exception in thread {}: {}", t.getName(), e.getMessage(), e)
        );

        String serverBaseUrl = System.getProperty("server.url", "http://localhost:8089");
        String clientId = System.getProperty("client.id", InetAddress.getLocalHost().getHostName()).toLowerCase();

        ConfigService configService = new ConfigService(serverBaseUrl, clientId);
        WebSocketClient wsClient = new WebSocketClient(serverBaseUrl, clientId, configService);

        Path queueDir = Paths.get(System.getProperty("java.io.tmpdir"), "auditclient");
        PersistentFileQueue queue = new PersistentFileQueue(queueDir);

        HttpUtil httpUtil = new HttpUtil(serverBaseUrl, clientId, configService);
        ScreenshotService screenshotService = new ScreenshotService(configService, queue);
        UploadService uploadService = new UploadService(queue, wsClient, httpUtil);
        IdleMonitor idleMonitor = new IdleMonitor(configService, screenshotService);
        HealthMonitor healthMonitor = new HealthMonitor(wsClient, uploadService, screenshotService, configService);

        configService.start();
        wsClient.start();
        screenshotService.start();
        Thread uploadThread = new Thread(uploadService, "UploadService-Thread");
        uploadThread.start();

        idleMonitor.start();
        healthMonitor.start();
        log.info("Client started...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down client...");
            try {
                healthMonitor.stop();
                idleMonitor.stop();
                uploadService.stop();
                screenshotService.stop();
                wsClient.stop();
                configService.stop();
                log.info("Shutdown complete.");
            } catch (Exception e) {
                log.error("Error during shutdown: {}", e.getMessage(), e);
            }
        }));
    }
}
