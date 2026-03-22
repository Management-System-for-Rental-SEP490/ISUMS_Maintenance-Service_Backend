package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobEvent;

import java.util.List;
import java.util.UUID;

public interface MaintenanceJobService {
    List<MaintenanceJobDto> generateMaintainJobs();
    List<MaintenanceJobDto> getAllJobs();
    MaintenanceJobDto getJobById(UUID jobId);
    List<MaintenanceJobDto> getJobByHouseId(UUID houseId);
    List<MaintenanceJobDto> getJobsByStatus(JobStatus status);
    void markScheduled(JobEvent event);
    void markRescheduled(JobEvent event);
    void markNeedReschedule(JobEvent event);
    MaintenanceJobDto updateJobStatus(UUID jobId, JobStatus newStatus);
    List<MaintenanceJobDto> getJobsByStaffId(String staffId);

}
