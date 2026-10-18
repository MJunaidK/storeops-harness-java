package com.storeops.reports.service;

import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.service.ActivityService;
import com.storeops.programmes.service.ProgrammeService;
import com.storeops.reports.model.Report;
import com.storeops.reports.model.ReportStatus;
import com.storeops.reports.model.ReportType;
import com.storeops.reports.repository.ReportRepository;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read-only consumer of the other modules: never writes to activities, programmes or staff. */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ActivityService activityService;
    private final ProgrammeService programmeService;
    private final StaffService staffService;

    public ReportService(ReportRepository reportRepository, ActivityService activityService,
                         ProgrammeService programmeService, StaffService staffService) {
        this.reportRepository = reportRepository;
        this.activityService = activityService;
        this.programmeService = programmeService;
        this.staffService = staffService;
    }

    public Report generate(AuthenticatedUser caller, ReportType type) {
        List<Task> tasks = activityService.list(null, null);
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("programmes", (long) programmeService.listForStore(caller).size());
        metrics.put("staff", (long) staffService.listByStore(caller.storeId()).size());
        metrics.put("tasks", (long) tasks.size());
        for (TaskStatus status : TaskStatus.values()) {
            metrics.put("tasks." + status.name(), tasks.stream().filter(t -> t.status() == status).count());
        }
        Report report = new Report(UUID.randomUUID().toString(), type, ReportStatus.READY, caller.storeId(), metrics,
            Instant.now());
        return reportRepository.save(report);
    }

    public Report get(String id) {
        return reportRepository.findById(id).orElseThrow(() -> new NotFoundError("Report not found: " + id));
    }
}
