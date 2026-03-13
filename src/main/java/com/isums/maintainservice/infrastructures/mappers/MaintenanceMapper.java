package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;
import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MaintenanceMapper {
    MaintenanceJobDto job(MaintenanceJob job);
    List<MaintenanceJobDto> jobs(List<MaintenanceJob> jobs);

    @Mapping(source = "job.id", target = "jobId")
    ExecutionDto ex(MaintenanceExecution executions);
    List<ExecutionDto> exs(List<MaintenanceExecution> executions);
}
