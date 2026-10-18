package com.storeops.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.service.ActivityService;
import com.storeops.programmes.model.Project;
import com.storeops.programmes.service.ProgrammeService;
import com.storeops.reports.model.Report;
import com.storeops.reports.model.ReportStatus;
import com.storeops.reports.model.ReportType;
import com.storeops.reports.repository.InMemoryReportRepository;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.StaffRole;
import com.storeops.staff.model.User;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final AuthenticatedUser CALLER = new AuthenticatedUser("m1", "s1", StaffRole.STORE_MANAGER);

    private final ActivityService activityService = mock(ActivityService.class);
    private final ProgrammeService programmeService = mock(ProgrammeService.class);
    private final StaffService staffService = mock(StaffService.class);
    private final ReportService service = new ReportService(new InMemoryReportRepository(), activityService, programmeService,
        staffService);

    private Task task(TaskStatus status) {
        Instant now = Instant.now();
        return new Task("t", "p", "title", null, status, TaskPriority.LOW, TaskCategory.GENERAL, null, "o", null, now, now);
    }

    @Test
    void generateAggregatesMetricsFromReadOnlyServices() {
        when(activityService.list(null, null)).thenReturn(List.of(task(TaskStatus.TODO), task(TaskStatus.DONE), task(TaskStatus.DONE)));
        when(programmeService.listForStore(CALLER))
            .thenReturn(List.of(new Project("p1", "n", "d", "s1", "m1", Instant.now(), List.of())));
        when(staffService.listByStore("s1")).thenReturn(List.of(new User("u", "n", "e", "s1", StaffRole.ASSOCIATE)));

        Report report = service.generate(CALLER, ReportType.STORE_SUMMARY);

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.storeId()).isEqualTo("s1");
        assertThat(report.metrics()).containsEntry("tasks", 3L).containsEntry("tasks.DONE", 2L)
            .containsEntry("tasks.TODO", 1L).containsEntry("tasks.BLOCKED", 0L)
            .containsEntry("programmes", 1L).containsEntry("staff", 1L);
        assertThat(service.get(report.id())).isEqualTo(report);
    }

    @Test
    void getUnknownReportIsNotFound() {
        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(NotFoundError.class);
    }
}
