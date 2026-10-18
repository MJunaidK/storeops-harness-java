package com.storeops.shared.error;

public abstract class AppError extends RuntimeException {

    private final String code;
    private final int statusCode;

    protected AppError(String code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
