package com.caerus.audit.client.enums;

public enum EventType {
  NORMAL(1),
  NETWORK_FAILURE(2),
  SERVICE_FAILED(3),
  CLIENT_STORAGE_FULL(4),
  SERVER_SETTING_PUSHED(5),
  CLIENT_CONFIG_REQUEST(6);

  private final int code;

  EventType(int code) {
    this.code = code;
  }

  public byte getCode() {
    return (byte) code;
  }
}
