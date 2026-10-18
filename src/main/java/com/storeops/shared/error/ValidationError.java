package com.storeops.shared.error;

public class ValidationError extends AppError {

    public ValidationError(String message) {
        super("VALIDATION_ERROR", message, 400);
    }
}
