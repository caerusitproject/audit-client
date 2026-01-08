package com.caerus.audit.client.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthMonitor {
  private final Logger log = LoggerFactory.getLogger(HealthMonitor.class);
  private final WebSocketClient ws;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public HealthMonitor(WebSocketClient ws) {
    this.ws = ws;
  }

  public void start() {
    scheduler.scheduleAtFixedRate(this::check, 2, 2, TimeUnit.MINUTES);
  }

  public void stop() {
    scheduler.shutdownNow();
  }

  private void check() {
    try {
      log.info("Health check triggered");
      log.info("WebSocket connected: {}", ws.isConnected());
      Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "auditclient");
      if (Files.exists(tmp)) {
        long size =
            Files.walk(tmp)
                .filter(Files::isRegularFile)
                .mapToLong(
                    p -> {
                      try {
                        return Files.size(p);
                      } catch (Exception e) {
                        return 0L;
                      }
                    })
                .sum();

        long mb = size / (1024 * 1024);
        log.info("Temp folder usage: {} MB", mb);
      }
    } catch (Exception e) {
      log.error("Health check failed: {}", e.getMessage());
    }
  }
}
