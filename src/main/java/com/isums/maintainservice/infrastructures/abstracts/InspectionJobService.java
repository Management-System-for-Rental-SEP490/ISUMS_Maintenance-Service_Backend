package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.CreateInspectionRequest;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobEvent;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;

import java.util.List;
import java.util.UUID;

public interface InspectionJobService {
    InspectionDto create(CreateInspectionRequest request);
    PageResponse<InspectionDto> getAll(PageRequest request);
    InspectionDto getInspectionById(UUID inspectionId);
    InspectionDto updateStatus(UUID id, InspectionStatus newStatus);
    void markScheduled(JobEvent event);
}
