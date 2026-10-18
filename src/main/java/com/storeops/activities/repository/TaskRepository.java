package com.storeops.activities.repository;

import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskStatus;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(String id);

    List<Task> findAll(String programmeId, TaskStatus status);

    boolean deleteById(String id);
}
