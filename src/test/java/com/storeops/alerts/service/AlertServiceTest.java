package com.storeops.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storeops.alerts.model.AlertType;
import com.storeops.alerts.model.Notification;
import com.storeops.alerts.model.NotificationChannel;
import com.storeops.alerts.model.NotificationStatus;
import com.storeops.alerts.repository.NotificationRepository;
import com.storeops.shared.event.TaskCreatedEvent;
import com.storeops.shared.event.TaskStatusChangedEvent;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.StaffRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private NotificationRepository repository;
    @InjectMocks
    private AlertService service;

    private List<Notification> captureSaved(int times) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void taskCreatedNotifiesAssignee() {
        service.onTaskCreated(new TaskCreatedEvent("t1", "Restock", "owner", "assignee", Instant.now()));

        Notification saved = captureSaved(1).get(0);
        assertThat(saved.userId()).isEqualTo("assignee");
        assertThat(saved.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(saved.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(saved.taskId()).isEqualTo("t1");
    }

    @Test
    void taskCreatedWithoutAssigneeNotifiesOwner() {
        service.onTaskCreated(new TaskCreatedEvent("t1", "Restock", "owner", null, Instant.now()));

        assertThat(captureSaved(1).get(0).userId()).isEqualTo("owner");
    }

    @Test
    void blockedStatusEscalatesToOwnerAndAssignee() {
        service.onTaskStatusChanged(new TaskStatusChangedEvent("t1", "Restock", "owner", "assignee", "TODO", "BLOCKED",
            Instant.now()));

        List<Notification> saved = captureSaved(2);
        assertThat(saved).extracting(Notification::userId).containsExactlyInAnyOrder("owner", "assignee");
        assertThat(saved).allMatch(n -> n.type() == AlertType.ESCALATION);
        assertThat(saved.get(0).message()).contains("TODO").contains("BLOCKED");
    }

    @Test
    void statusChangeWhenOwnerIsAssigneeNotifiesOnce() {
        service.onTaskStatusChanged(new TaskStatusChangedEvent("t1", "Restock", "same", "same", "TODO", "DONE", Instant.now()));

        Notification saved = captureSaved(1).get(0);
        assertThat(saved.type()).isEqualTo(AlertType.SHIFT_HANDOVER);
    }

    @Test
    void statusChangeWithoutAssigneeNotifiesOnlyOwner() {
        service.onTaskStatusChanged(new TaskStatusChangedEvent("t1", "Restock", "owner", null, "TODO", "DONE", Instant.now()));

        assertThat(captureSaved(1).get(0).userId()).isEqualTo("owner");
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void listForUserReturnsCallersNotifications() {
        Notification n = new Notification("n1", "u1", AlertType.INVENTORY, NotificationChannel.IN_APP, NotificationStatus.UNREAD,
            "t", "m", null, Instant.now());
        when(repository.findByUserId("u1")).thenReturn(List.of(n));

        assertThat(service.listForUser(new AuthenticatedUser("u1", "s1", StaffRole.ASSOCIATE))).containsExactly(n);
    }
}
