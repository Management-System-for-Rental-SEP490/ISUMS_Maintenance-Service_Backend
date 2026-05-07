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
    @Mapping(target = "staffName", ignore = true)
    @Mapping(target = "staffPhone", ignore = true)
    @Mapping(target = "staff", ignore = true)
    MaintenanceJobDto job(MaintenanceJob job);
    List<MaintenanceJobDto> jobs(List<MaintenanceJob> jobs);

    @Mapping(source = "job.id", target = "jobId")
    @Mapping(target = "notes", expression = "java(resolveNotes(executions))")
    ExecutionDto ex(MaintenanceExecution executions);
    List<ExecutionDto> exs(List<MaintenanceExecution> executions);

    default String resolveNotes(MaintenanceExecution execution) {
        if (execution == null) return null;
        if (execution.getNotesTranslations() != null) {
            String resolved = execution.getNotesTranslations().resolve();
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return execution.getNotes();
    }
}
