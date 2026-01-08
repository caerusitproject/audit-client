package com.caerus.audit.client;

import com.caerus.audit.client.config.ClientConfig;
import com.caerus.audit.client.queue.PersistentFileQueue;
import com.caerus.audit.client.service.*;
import com.caerus.audit.client.util.AdminCheckUtil;
import com.caerus.audit.client.util.HttpUtil;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
  private static final Logger log = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) ->
            log.error("Unhandled exception in thread {}: {}", t.getName(), e.getMessage(), e));

    try {
      if (AdminCheckUtil.isCurrentUserAdmin()) {
        log.info("Admin detected — skipping monitoring.");
        return;
      }

      String serverBaseUrl = ClientConfig.required("server.baseUrl");
      String clientId = ClientConfig.optional(
              "client.id", InetAddress.getLocalHost().getHostAddress()
      );
      String ipAddress = InetAddress.getLocalHost().getHostAddress();

      Path queueDir = Paths.get(ClientConfig.optional(
              "queue.baseDir", System.getProperty("java.io.tmpdir") + "/auditclient"
      ));

      log.info("Starting Audit Client [clientId={}, server={}]", clientId, serverBaseUrl);

      EventReporter eventReporter = new EventReporter(serverBaseUrl, clientId, ipAddress);
      ConfigService configService = new ConfigService(serverBaseUrl, clientId);
      WebSocketClient wsClient = new WebSocketClient(serverBaseUrl, clientId);

      PersistentFileQueue queue = new PersistentFileQueue(queueDir);

      HttpUtil httpUtil = new HttpUtil(serverBaseUrl, clientId);
      ScreenshotService screenshotService =
          new ScreenshotService(configService, queue, eventReporter);
      UploadService uploadService = new UploadService(queue, wsClient, httpUtil, eventReporter);
      IdleMonitor idleMonitor = new IdleMonitor(configService, screenshotService, eventReporter);
      HealthMonitor healthMonitor = new HealthMonitor(wsClient);
      WorkstationStateMonitor workstationMonitor = new WorkstationStateMonitor(screenshotService);

      configService.start();
      wsClient.start();
      screenshotService.start();
      idleMonitor.start();
      healthMonitor.start();
      workstationMonitor.start();
      uploadService.start();

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    log.info("Shutting down client...");
                    try {
                      healthMonitor.stop();
                      idleMonitor.stop();
                      screenshotService.stop();
                      wsClient.stop();
                      configService.stop();
                      workstationMonitor.stop();
                      log.info("Shutdown complete.");
                    } catch (Exception e) {
                      log.error("Error during shutdown: {}", e.getMessage(), e);
                    }
                  }));
    } catch (Exception e) {
      log.error("Fatal startup error in Audit Client: {}", e.getMessage(), e);
      System.exit(1);
    }
  }
}
