package com.caerus.audit.client.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Bootstrap configuration loader for Audit Client.
 *
 * <p>Rule: - Config MUST be provided externally
 */
public class ClientConfig {
  private static final Properties PROPS = new Properties();

  static {
    try {
      String configPath = System.getProperty("audit.config");
      if (configPath == null || configPath.isBlank()) {
        throw new IllegalStateException(
            "Missing JVM argument: -Daudit.config=<path-to-audit-client.properties>");
      }
      Path path = Paths.get(configPath).toAbsolutePath();
      if (!Files.exists(path)) {
        throw new IllegalStateException("Config file not found: " + path);
      }
      try (InputStream in = Files.newInputStream(path)) {
        PROPS.load(in);
      }
    } catch (Exception e) {
      System.err.println("Audit Client startup failed");
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  private ClientConfig() {}

  public static String required(String key) {
    String sysVal = System.getProperty(key);
    if (sysVal != null && !sysVal.isBlank()) {
      return sysVal.trim();
    }

    String val = PROPS.getProperty(key);
    if (val == null || val.isBlank()) {
      throw new IllegalStateException("Missing required config key: " + key);
    }
    return val.trim();
  }

  public static String optional(String key, String defaultVal) {
    String sysVal = System.getProperty(key);
    if (sysVal != null && !sysVal.isBlank()) {
      return sysVal.trim();
    }
    return PROPS.getProperty(key, defaultVal).trim();
  }

  public static int optionalInt(String key, int defaultVal) {
    return Integer.parseInt(optional(key, String.valueOf(defaultVal)));
  }
}
