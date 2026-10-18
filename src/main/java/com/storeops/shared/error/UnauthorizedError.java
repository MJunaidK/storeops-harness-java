package com.storeops.shared.error;

public class UnauthorizedError extends AppError {

    public UnauthorizedError(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}
