package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;

import java.util.List;
import java.util.UUID;

public interface MaintenanceJobHistoryService {
    List<MaintenanceJobHistory> getJobHistory(UUID jobId);
}
