package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.CreateInspectionRequest;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.JobStatus;

import java.util.List;
import java.util.UUID;

public interface InspectionJobService {
    InspectionDto create(CreateInspectionRequest request);
    List<InspectionDto> getAll(InspectionStatus status);
    InspectionDto getInspectionById(UUID inspectionId);
    InspectionDto updateStatus(UUID id, InspectionStatus newStatus);
}
