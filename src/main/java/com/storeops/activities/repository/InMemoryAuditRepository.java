package com.storeops.activities.repository;

import com.storeops.activities.model.AuditEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public AuditEntry save(AuditEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public List<AuditEntry> findByTaskId(String taskId) {
        return entries.stream().filter(e -> e.taskId().equals(taskId)).toList();
    }
}
