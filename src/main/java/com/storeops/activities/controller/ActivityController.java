package com.storeops.activities.controller;

import com.storeops.activities.dto.BulkStatusRequest;
import com.storeops.activities.dto.BulkStatusResponse;
import com.storeops.activities.dto.CreateTaskRequest;
import com.storeops.activities.dto.UpdateTaskRequest;
import com.storeops.activities.model.AuditEntry;
import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.service.ActivityService;
import com.storeops.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public List<Task> list(AuthenticatedUser caller, @RequestParam(required = false) String programmeId,
                           @RequestParam(required = false) TaskStatus status) {
        return activityService.list(programmeId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(AuthenticatedUser caller, @Valid @RequestBody CreateTaskRequest request) {
        return activityService.create(caller, request);
    }

    @GetMapping("/{id}")
    public Task get(AuthenticatedUser caller, @PathVariable String id) {
        return activityService.get(id);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEntry> listAudit(AuthenticatedUser caller, @PathVariable String id) {
        return activityService.listAudit(caller, id);
    }

    @PatchMapping("/bulk-status")
    public BulkStatusResponse updateStatusBulk(AuthenticatedUser caller, @Valid @RequestBody BulkStatusRequest request) {
        return activityService.updateStatusBulk(caller, request);
    }

    @PatchMapping("/{id}")
    public Task update(AuthenticatedUser caller, @PathVariable String id, @RequestBody UpdateTaskRequest request) {
        return activityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(AuthenticatedUser caller, @PathVariable String id) {
        activityService.delete(caller, id);
    }
}
