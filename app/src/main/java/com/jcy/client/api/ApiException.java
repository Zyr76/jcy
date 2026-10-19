package com.jcy.client.api;

public final class ApiException extends Exception {
    public final int code;

    public ApiException(int code, String message) {
        super("API " + code + ": " + message);
        this.code = code;
    }
}
