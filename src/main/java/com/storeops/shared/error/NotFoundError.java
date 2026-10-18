package com.storeops.shared.error;

public class NotFoundError extends AppError {

    public NotFoundError(String message) {
        super("NOT_FOUND", message, 404);
    }
}
