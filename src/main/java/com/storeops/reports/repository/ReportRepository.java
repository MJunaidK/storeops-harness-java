package com.storeops.reports.repository;

import com.storeops.reports.model.Report;
import java.util.Optional;

public interface ReportRepository {

    Report save(Report report);

    Optional<Report> findById(String id);
}
