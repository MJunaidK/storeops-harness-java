package com.storeops.activities.dto;

public record BulkStatusResult(String taskId, boolean success, String status, String errorCode, String message) {
}
