package com.storeops.alerts.service;

import com.storeops.alerts.model.AlertType;
import com.storeops.alerts.model.Notification;
import com.storeops.alerts.model.NotificationChannel;
import com.storeops.alerts.model.NotificationStatus;
import com.storeops.alerts.repository.NotificationRepository;
import com.storeops.shared.event.TaskCreatedEvent;
import com.storeops.shared.event.TaskStatusChangedEvent;
import com.storeops.shared.security.AuthenticatedUser;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private static final String BLOCKED = "BLOCKED";

    private final NotificationRepository notificationRepository;

    public AlertService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> listForUser(AuthenticatedUser caller) {
        return notificationRepository.findByUserId(caller.id());
    }

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        String recipient = event.assigneeId() != null ? event.assigneeId() : event.ownerId();
        notify(recipient, AlertType.SHIFT_HANDOVER, "New task: " + event.title(),
            "Task " + event.taskId() + " was created", event.taskId(), event.occurredAt());
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        AlertType type = BLOCKED.equals(event.newStatus()) ? AlertType.ESCALATION : AlertType.SHIFT_HANDOVER;
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(event.ownerId());
        recipients.add(event.assigneeId());
        recipients.stream().filter(Objects::nonNull).forEach(userId -> notify(userId, type,
            "Task status changed: " + event.title(),
            "Task " + event.taskId() + " moved from " + event.oldStatus() + " to " + event.newStatus(),
            event.taskId(), event.occurredAt()));
    }

    private void notify(String userId, AlertType type, String title, String message, String taskId, Instant at) {
        notificationRepository.save(new Notification(UUID.randomUUID().toString(), userId, type, NotificationChannel.IN_APP,
            NotificationStatus.UNREAD, title, message, taskId, at));
    }
}
