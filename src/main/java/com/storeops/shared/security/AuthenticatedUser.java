package com.storeops.shared.security;

import com.storeops.staff.model.StaffRole;

public record AuthenticatedUser(String id, String storeId, StaffRole role) {
}
