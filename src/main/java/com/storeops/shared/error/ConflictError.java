package com.storeops.shared.error;

public class ConflictError extends AppError {

    public ConflictError(String message) {
        super("CONFLICT", message, 409);
    }
}
