package com.storeops.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void appErrorsMapToTheirStatusAndCode() {
        assertMapped(new NotFoundError("m"), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertMapped(new ValidationError("m"), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertMapped(new UnauthorizedError("m"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertMapped(new ForbiddenError("m"), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertMapped(new ConflictError("m"), HttpStatus.CONFLICT, "CONFLICT");
    }

    @Test
    void malformedRequestMapsToValidationFormat() {
        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequest(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("VALIDATION_ERROR", "Malformed request"));
    }

    private void assertMapped(AppError error, HttpStatus status, String code) {
        ResponseEntity<ErrorResponse> response = handler.handleAppError(error);

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(code, "m"));
        assertThat(error.getStatusCode()).isEqualTo(status.value());
    }
}
