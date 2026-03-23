package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.MaintenanceExecution.CreateExecutionRequest;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;

import java.util.List;
import java.util.UUID;

public interface MaintenanceExecutionService {
    ExecutionDto createExecution(String staffId, CreateExecutionRequest req);
    List<ExecutionDto> getExecutionsByJobId(UUID jobId);
    List<ExecutionDto> getAllExecutions();
    List<ExecutionDto> getExecutionsByHouseId(UUID houseId);
}
