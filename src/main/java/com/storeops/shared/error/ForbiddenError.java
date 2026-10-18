package com.storeops.shared.error;

public class ForbiddenError extends AppError {

    public ForbiddenError(String message) {
        super("FORBIDDEN", message, 403);
    }
}
