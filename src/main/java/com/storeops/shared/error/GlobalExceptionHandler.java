package com.storeops.shared.error;

import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppError.class)
    public ResponseEntity<ErrorResponse> handleAppError(AppError error) {
        return ResponseEntity.status(error.getStatusCode()).body(new ErrorResponse(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        return handleAppError(new ValidationError(message.isEmpty() ? "Request validation failed" : message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingRequestValueException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        return handleAppError(new ValidationError("Malformed request"));
    }
}
