package com.storeops.activities.repository;

import com.storeops.activities.model.AuditEntry;
import java.util.List;

public interface AuditRepository {

    AuditEntry save(AuditEntry entry);

    List<AuditEntry> findByTaskId(String taskId);
}
