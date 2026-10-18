package com.storeops.staff.model;

public record User(String id, String name, String email, String storeId, StaffRole role) {
}
