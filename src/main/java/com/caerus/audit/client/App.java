package com.caerus.audit.client;

import com.caerus.audit.client.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

public class App
{
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main( String[] args ) throws Exception{
        String serverBaseUrl = System.getProperty("server.url", "http://localhost:8089");
        String clientId = System.getProperty("client.id", InetAddress.getLocalHost().getHostName()).toLowerCase();

        ConfigService configService = new ConfigService(serverBaseUrl, clientId);
        WebSocketClient ws = new WebSocketClient(serverBaseUrl, clientId, configService);
        FileQueue fileQueue = new FileQueue();
        ScreenshotService screenshotService = new ScreenshotService(configService, fileQueue);
        UploadService uploadService = new UploadService(serverBaseUrl, clientId, fileQueue, ws, configService);
        IdleMonitor idleMonitor = new IdleMonitor(configService, screenshotService);
        HealthMonitor healthMonitor = new HealthMonitor(ws, uploadService, screenshotService, configService);

        configService.start();
        ws.start();
        screenshotService.start();
        uploadService.start();
        idleMonitor.start();
        healthMonitor.start();
        log.info("Client started...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down client...");
            healthMonitor.stop();
            idleMonitor.stop();
            uploadService.stop();
            screenshotService.stop();
            ws.stop();
            configService.stop();
            log.info("Shutdown complete.");
        }));
    }
}
