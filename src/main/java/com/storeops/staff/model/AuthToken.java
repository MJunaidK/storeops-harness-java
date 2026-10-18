package com.storeops.staff.model;

import java.time.Instant;

public record AuthToken(String token, String userId, Instant expiresAt) {
}
