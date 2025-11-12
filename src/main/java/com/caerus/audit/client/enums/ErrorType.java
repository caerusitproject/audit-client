package com.caerus.audit.client.enums;

public enum ErrorType {
    NORMAL(1),
    CONNECTION_ERROR(2),
    STORAGE_ERROR(3);

    private final int code;

    ErrorType(int code) {
        this.code = code;
    }

    public byte getCode() {
        return (byte) code;
    }
}
