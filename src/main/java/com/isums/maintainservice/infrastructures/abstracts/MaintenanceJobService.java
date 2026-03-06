package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;

import java.util.List;
import java.util.UUID;

public interface MaintenanceJobService {
    List<MaintenanceJobDto> generateMaintainJobs();
    List<MaintenanceJobDto> getAllJobs();
    MaintenanceJobDto getJobById(UUID jobId);
    List<MaintenanceJobDto> getJobByHouseId(UUID houseId);
}
